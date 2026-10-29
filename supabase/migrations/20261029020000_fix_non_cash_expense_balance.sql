-- Fix operational expense balance validation for the combined Non-tunai source.
-- Non-tunai consists of QRIS + bank transfer + e-wallet. The UI stores this
-- combined source as sales_transfer, so the RPC must validate the same group.
CREATE OR REPLACE FUNCTION public.km_record_operational_expense(
 p_owner_id uuid,
 p_amount numeric,
 p_category text,
 p_description text,
 p_expense_date date,
 p_payment_method text,
 p_cash_source text,
 p_idempotency_key uuid,
 p_shift_id uuid DEFAULT NULL::uuid
) RETURNS SETOF public.expenses
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $function$
DECLARE
  v_owner uuid;
  v_existing public.expenses%rowtype;
  v_sales numeric := 0;
  v_transfers numeric := 0;
  v_paid_out numeric := 0;
  v_target numeric := 0;
  v_fund_in numeric := 0;
  v_fund_out numeric := 0;
  v_available numeric := 0;
  v_session_start timestamptz;
  v_last_close timestamptz;
BEGIN
  IF (SELECT auth.uid()) IS NULL THEN RAISE EXCEPTION 'Login diperlukan.' USING errcode='28000'; END IF;
  v_owner := public.km_resolve_owner_id();
  IF p_owner_id IS DISTINCT FROM v_owner THEN RAISE EXCEPTION 'Akses outlet ditolak.' USING errcode='42501'; END IF;
  IF p_amount IS NULL OR p_amount <= 0 OR p_amount > 1000000000 THEN RAISE EXCEPTION 'Nominal pengeluaran tidak valid.'; END IF;
  IF nullif(trim(p_category),'') IS NULL OR nullif(trim(p_description),'') IS NULL THEN RAISE EXCEPTION 'Kategori dan keterangan wajib diisi.'; END IF;
  IF p_cash_source NOT IN ('cash_fund','sales_cash','sales_qris','sales_transfer','owner_funds','other') THEN RAISE EXCEPTION 'Sumber dana tidak valid.'; END IF;
  IF p_idempotency_key IS NULL THEN RAISE EXCEPTION 'Kunci transaksi wajib ada.'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_owner::text || ':cash', 0));
  SELECT * INTO v_existing FROM public.expenses WHERE owner_id=v_owner AND idempotency_key=p_idempotency_key LIMIT 1;
  IF FOUND THEN RETURN NEXT v_existing; RETURN; END IF;
  IF p_shift_id IS NOT NULL AND NOT EXISTS(
    SELECT 1 FROM public.staff_shifts s WHERE s.id=p_shift_id AND s.owner_id=v_owner AND s.status='open' AND s.staff_user_id=(SELECT auth.uid())
  ) THEN RAISE EXCEPTION 'Shift aktif tidak ditemukan atau bukan milik kasir ini.' USING errcode='42501'; END IF;
  SELECT max(created_at) INTO v_last_close FROM public.cash_fund_entries WHERE owner_id=v_owner AND entry_type='close_session';
  SELECT max(created_at) INTO v_session_start FROM public.cash_fund_entries
    WHERE owner_id=v_owner AND entry_type='set_target' AND (v_last_close IS NULL OR created_at > v_last_close);
  IF v_session_start IS NULL THEN RAISE EXCEPTION 'Mulai sesi kasir terlebih dahulu.' USING errcode='23514'; END IF;
  IF p_cash_source = 'cash_fund' THEN
    SELECT coalesce(sum(amount) FILTER (WHERE entry_type='set_target'),0),
           coalesce(sum(amount) FILTER (WHERE entry_type='replenishment'),0)
      INTO v_target,v_fund_in FROM public.cash_fund_entries
      WHERE owner_id=v_owner AND created_at >= v_session_start;
    SELECT coalesce(sum(amount),0) INTO v_fund_out FROM public.expenses
      WHERE owner_id=v_owner AND cash_source='cash_fund' AND created_at >= v_session_start;
    v_available := v_target + v_fund_in - v_fund_out;
  ELSIF p_cash_source IN ('sales_cash','sales_qris','sales_transfer') THEN
    SELECT coalesce(sum(p.amount),0) INTO v_sales
      FROM public.payments p JOIN public.orders o ON o.id=p.order_id
      WHERE (o.outlet_id IN (SELECT id FROM public.outlets WHERE owner_id=v_owner) OR o.outlet_id IS NULL)
        AND o.created_at >= v_session_start AND o.is_paid=true AND o.status <> 'cancelled' AND o.returned_at IS NULL
        AND CASE
          WHEN p_cash_source='sales_cash' THEN
            (lower(coalesce(p.method,'')) LIKE '%cash%' OR lower(coalesce(p.method,'')) LIKE '%tunai%')
          WHEN p_cash_source='sales_qris' THEN lower(coalesce(p.method,'')) LIKE '%qris%'
          ELSE (
            lower(coalesce(p.method,'')) LIKE '%qris%' OR lower(coalesce(p.method,'')) LIKE '%transfer%'
            OR lower(coalesce(p.method,'')) LIKE '%bank%' OR lower(coalesce(p.method,'')) LIKE '%bca%'
            OR lower(coalesce(p.method,'')) LIKE '%bni%' OR lower(coalesce(p.method,'')) LIKE '%bri%'
            OR lower(coalesce(p.method,'')) LIKE '%mandiri%' OR lower(coalesce(p.method,'')) LIKE '%permata%'
            OR lower(coalesce(p.method,'')) LIKE '%e_wallet%' OR lower(coalesce(p.method,'')) LIKE '%ewallet%'
            OR lower(coalesce(p.method,'')) LIKE '%e wallet%' OR lower(coalesce(p.method,'')) LIKE '%e-wallet%'
            OR lower(coalesce(p.method,'')) LIKE '%dana%' OR lower(coalesce(p.method,'')) LIKE '%gopay%'
            OR lower(coalesce(p.method,'')) LIKE '%go pay%' OR lower(coalesce(p.method,'')) LIKE '%ovo%'
            OR lower(coalesce(p.method,'')) LIKE '%shopeepay%' OR lower(coalesce(p.method,'')) LIKE '%linkaja%'
          )
        END;
    SELECT v_sales + coalesce(sum(o.total),0) INTO v_sales FROM public.orders o
      WHERE (o.outlet_id IN (SELECT id FROM public.outlets WHERE owner_id=v_owner) OR o.outlet_id IS NULL)
        AND o.created_at >= v_session_start AND o.is_paid=true AND o.status <> 'cancelled' AND o.returned_at IS NULL
        AND NOT EXISTS(SELECT 1 FROM public.payments p WHERE p.order_id=o.id)
        AND CASE
          WHEN p_cash_source='sales_cash' THEN
            (lower(coalesce(o.payment_method,'')) LIKE '%cash%' OR lower(coalesce(o.payment_method,'')) LIKE '%tunai%')
          WHEN p_cash_source='sales_qris' THEN lower(coalesce(o.payment_method,'')) LIKE '%qris%'
          ELSE (
            lower(coalesce(o.payment_method,'')) LIKE '%qris%' OR lower(coalesce(o.payment_method,'')) LIKE '%transfer%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bank%' OR lower(coalesce(o.payment_method,'')) LIKE '%bca%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bni%' OR lower(coalesce(o.payment_method,'')) LIKE '%bri%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%mandiri%' OR lower(coalesce(o.payment_method,'')) LIKE '%permata%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%e_wallet%' OR lower(coalesce(o.payment_method,'')) LIKE '%ewallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%e wallet%' OR lower(coalesce(o.payment_method,'')) LIKE '%e-wallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%dana%' OR lower(coalesce(o.payment_method,'')) LIKE '%gopay%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%go pay%' OR lower(coalesce(o.payment_method,'')) LIKE '%ovo%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%shopeepay%' OR lower(coalesce(o.payment_method,'')) LIKE '%linkaja%'
          )
        END;
    IF p_cash_source='sales_transfer' THEN
      SELECT coalesce(sum(amount),0) INTO v_transfers FROM public.cash_fund_entries
        WHERE owner_id=v_owner AND entry_type='replenishment' AND source IN ('sales_qris','sales_transfer') AND created_at >= v_session_start;
    ELSE
      SELECT coalesce(sum(amount),0) INTO v_transfers FROM public.cash_fund_entries
        WHERE owner_id=v_owner AND entry_type='replenishment' AND source=p_cash_source AND created_at >= v_session_start;
    END IF;
    SELECT coalesce(sum(amount),0) INTO v_paid_out FROM public.expenses
      WHERE owner_id=v_owner AND cash_source=p_cash_source AND created_at >= v_session_start;
    v_available := v_sales - v_transfers - v_paid_out;
  ELSE
    v_available := 1000000000000;
  END IF;
  IF v_available < p_amount THEN
    RAISE EXCEPTION 'Saldo sumber dana tidak cukup. Tersedia: %', greatest(v_available,0) USING errcode='23514';
  END IF;
  INSERT INTO public.expenses(owner_id,amount,category,description,expense_date,payment_method,cash_source,created_by,idempotency_key,shift_id)
  VALUES(v_owner,p_amount,trim(p_category),trim(p_description),coalesce(p_expense_date,current_date),coalesce(nullif(p_payment_method,''),'cash'),p_cash_source,(SELECT auth.uid()),p_idempotency_key,p_shift_id)
  RETURNING * INTO v_existing;
  RETURN NEXT v_existing;
END;
$function$;
