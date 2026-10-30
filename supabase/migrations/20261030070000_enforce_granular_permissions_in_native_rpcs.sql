DO $do$
BEGIN
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.adjust_product_stock(p_product_id uuid, p_delta integer, p_note text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE v_uid uuid := auth.uid(); v_owner uuid; v_product public.products%rowtype; v_next integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Sesi login tidak ditemukan'; END IF;
  IF NOT public.has_permission('inventory.adjust_stock') THEN RAISE EXCEPTION 'Akun tidak punya izin inventory'; END IF;
  IF p_product_id IS NULL OR p_delta IS NULL OR p_delta = 0 THEN RAISE EXCEPTION 'Produk dan perubahan stok tidak valid'; END IF;
  SELECT coalesce(sp.owner_id,v_uid) INTO v_owner FROM public.staff_profiles sp WHERE sp.user_id=v_uid;
  v_owner := coalesce(v_owner,v_uid);
  SELECT * INTO v_product FROM public.products WHERE id=p_product_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Produk tidak ditemukan'; END IF;
  v_next := coalesce(v_product.stock,0)+p_delta;
  IF v_next < 0 THEN RAISE EXCEPTION 'Stok tidak boleh negatif'; END IF;
  UPDATE public.products SET stock=v_next WHERE id=p_product_id;
  INSERT INTO public.inventory_movements(product_id,owner_id,type,quantity,note,created_by)
  VALUES(p_product_id,v_owner,CASE WHEN p_delta>0 THEN 'purchase' ELSE 'adjustment' END,p_delta,nullif(trim(p_note),''),v_uid);
  RETURN jsonb_build_object('product_id',p_product_id,'previous_stock',v_product.stock,'stock',v_next);
END; $function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.approve_qr_order_with_method(p_order_id uuid, p_payment_method text, p_cash_received numeric DEFAULT NULL::numeric)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_order public.orders%rowtype;
  v_staff_active boolean := false;
  v_method text;
  v_cash numeric;
  v_change numeric := 0;
BEGIN
  IF NOT public.has_permission('payments.confirm') THEN RAISE EXCEPTION 'Akun tidak punya izin konfirmasi pembayaran'; END IF;
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Sesi login tidak ditemukan';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM public.staff_profiles sp
    WHERE (sp.user_id = auth.uid() OR sp.user_id IS NULL AND false)
      AND sp.is_active = true
  ) OR EXISTS (
    SELECT 1 FROM public.staff_profiles sp
    WHERE sp.user_id = auth.uid() AND sp.is_active = true
  ) INTO v_staff_active;

  IF NOT v_staff_active THEN
    SELECT EXISTS (
      SELECT 1 FROM public.staff_profiles sp
      WHERE sp.user_id = auth.uid() AND sp.is_active = true
    ) INTO v_staff_active;
  END IF;

  IF NOT v_staff_active THEN
    RAISE EXCEPTION 'Profil staf aktif tidak ditemukan. Minta owner mengaktifkan akun kasir.';
  END IF;

  SELECT o.* INTO v_order
  FROM public.orders o
  WHERE o.id = p_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Pesanan tidak ditemukan';
  END IF;

  IF v_order.source <> 'qr'
     OR v_order.status NOT IN ('new', 'preparing', 'ready')
     OR v_order.is_paid THEN
    RAISE EXCEPTION 'Pesanan bukan order QR aktif yang menunggu pembayaran';
  END IF;

  v_method := lower(trim(coalesce(p_payment_method, '')));
  IF v_method = '' OR (
    v_method NOT IN ('cash','qris','card','bank_transfer','ewallet')
    AND NOT EXISTS (
      SELECT 1 FROM public.payment_methods pm
      WHERE lower(pm.code) = v_method AND pm.is_active = true
    )
  ) THEN
    RAISE EXCEPTION 'Metode pembayaran tidak valid atau tidak aktif';
  END IF;

  IF v_method = 'cash' OR v_method LIKE 'cash-%' THEN
    IF p_cash_received IS NULL OR p_cash_received < v_order.total THEN
      RAISE EXCEPTION 'Uang diterima belum mencukupi';
    END IF;
    v_cash := p_cash_received;
    v_change := p_cash_received - v_order.total;
  ELSE
    v_cash := NULL;
  END IF;

  UPDATE public.orders
  SET payment_method = v_method,
      is_paid = true,
      payment_status = 'paid',
      status = CASE WHEN v_order.status = 'new' THEN 'preparing' ELSE v_order.status END,
      updated_at = now()
  WHERE id = p_order_id
  RETURNING * INTO v_order;

  INSERT INTO public.payments (order_id, method, amount, cash_received, change_amount)
  VALUES (p_order_id, v_method, v_order.total, v_cash, v_change);

  RETURN v_order;
END;
$function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.confirm_order_payment(p_order_id uuid, p_payment_method text, p_cash_received numeric DEFAULT NULL::numeric)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
  v_order public.orders%rowtype;
  v_owner_id uuid;
  v_method text := lower(trim(coalesce(p_payment_method, '')));
  v_type text;
  v_cash numeric;
  v_change numeric := 0;
begin
  if auth.uid() is null then
    raise exception 'Sesi login tidak ditemukan';
  end if;

  select o.* into v_order
  from public.orders o
  join public.outlets ot on ot.id = o.outlet_id
  where o.id = p_order_id
  for update of o;

  if not found then
    raise exception 'Pesanan tidak ditemukan';
  end if;

  select sp.owner_id into v_owner_id
  from public.staff_profiles sp
  where sp.user_id = auth.uid() and sp.is_active = true
  limit 1;

  if v_owner_id is null then
    select sp.owner_id into v_owner_id
    from public.staff_profiles sp
    where sp.id = auth.uid() and sp.is_active = true
    limit 1;
  end if;

  if v_owner_id is null or not exists (
    select 1 from public.outlets ot
    where ot.id = v_order.outlet_id and ot.owner_id = v_owner_id
  ) then
    raise exception 'Pesanan tidak berada di outlet yang dapat diakses';
  end if;

  if not public.has_permission('payments.confirm') then
    raise exception 'Tidak memiliki izin konfirmasi pembayaran';
  end if;

  if v_order.status not in ('new', 'preparing', 'ready') or v_order.is_paid then
    raise exception 'Pesanan tidak aktif atau sudah dibayar';
  end if;

  select pm.type into v_type
  from public.payment_methods pm
  where pm.owner_id = v_owner_id
    and lower(pm.code) = v_method
    and pm.is_active = true
  limit 1;

  if v_type is null then
    if v_method not in ('cash', 'qris', 'card', 'bank_transfer', 'ewallet') then
      raise exception 'Metode pembayaran tidak valid atau tidak aktif';
    end if;
    v_type := v_method;
  end if;

  if v_type = 'cash' or v_method = 'cash' then
    if p_cash_received is null or p_cash_received < v_order.total then
      raise exception 'Uang diterima belum mencukupi';
    end if;
    v_cash := p_cash_received;
    v_change := p_cash_received - v_order.total;
  else
    v_cash := null;
  end if;

  update public.orders
  set payment_method = v_method,
      is_paid = true,
      payment_status = 'paid',
      status = case when v_order.status = 'new' then 'preparing' else v_order.status end,
      updated_at = now()
  where id = p_order_id
  returning * into v_order;

  insert into public.payments (order_id, method, amount, cash_received, change_amount)
  values (p_order_id, v_method, v_order.total, v_cash, v_change);

  return v_order;
end;
$function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.create_pos_order(p_order jsonb, p_items jsonb, p_payment jsonb DEFAULT NULL::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_uid uuid := auth.uid();
  v_owner uuid;
  v_role text;
  v_order public.orders%rowtype;
  v_item jsonb;
  v_product public.products%rowtype;
  v_promotion public.promotions%rowtype;
  v_key uuid := nullif(p_order->>'idempotency_key','')::uuid;
  v_promotion_id uuid := nullif(p_order->>'promotion_id','')::uuid;
  v_subtotal numeric := 0;
  v_manual numeric := coalesce(nullif(p_order->>'discount','')::numeric,0);
  v_promo numeric := 0;
  v_discount numeric := 0;
  v_service numeric := 0;
  v_tax numeric := 0;
  v_total numeric := 0;
  v_base numeric := 0;
  v_unit_price numeric := 0;
  v_service_rate numeric := 0;
  v_tax_rate numeric := 0;
  v_service_enabled boolean := false;
  v_tax_enabled boolean := false;
  v_qty integer;
  v_product_id uuid;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Sesi login tidak ditemukan'; END IF;
  IF NOT public.has_permission('pos.create_order') THEN
    RAISE EXCEPTION 'Akun tidak punya izin transaksi';
  END IF;
  IF v_key IS NULL THEN RAISE EXCEPTION 'Idempotency key wajib diisi'; END IF;
  IF jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
    RAISE EXCEPTION 'Pesanan harus memiliki minimal satu item';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(v_key::text, 0));
  SELECT * INTO v_order FROM public.orders WHERE idempotency_key = v_key;
  IF FOUND THEN RETURN jsonb_build_object('order',to_jsonb(v_order),'idempotent_replay',true); END IF;

  SELECT coalesce(sp.owner_id,v_uid), sp.role INTO v_owner,v_role
  FROM public.staff_profiles sp WHERE sp.user_id=v_uid AND sp.is_active;
  v_owner := coalesce(v_owner,v_uid);

  SELECT coalesce(os.service_rate,0), coalesce(os.tax_rate,0),
         coalesce(os.service_enabled,false), coalesce(os.tax_enabled,false)
    INTO v_service_rate,v_tax_rate,v_service_enabled,v_tax_enabled
  FROM public.outlet_settings os WHERE os.owner_id=v_owner LIMIT 1;

  PERFORM p.id FROM public.products p
    WHERE p.id IN (
      SELECT DISTINCT (x->>'product_id')::uuid
      FROM jsonb_array_elements(p_items) x
      WHERE nullif(x->>'product_id','') IS NOT NULL
    )
    ORDER BY p.id FOR UPDATE;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
    v_product_id := nullif(v_item->>'product_id','')::uuid;
    v_qty := coalesce((v_item->>'quantity')::integer,0);
    IF v_product_id IS NULL OR v_qty <= 0 THEN RAISE EXCEPTION 'Item atau quantity tidak valid'; END IF;
    SELECT * INTO v_product FROM public.products WHERE id=v_product_id AND is_active=true;
    IF NOT FOUND THEN RAISE EXCEPTION 'Produk tidak ditemukan atau tidak aktif'; END IF;
    v_unit_price := public.calculate_pos_item_unit_price(v_product_id, v_item);
    v_subtotal := v_subtotal + v_unit_price * v_qty;
  END LOOP;

  IF v_manual < 0 THEN RAISE EXCEPTION 'Diskon manual tidak boleh negatif'; END IF;
  IF coalesce(v_role,'cashier') NOT IN ('owner','super_admin')
     AND v_manual > round(v_subtotal * 0.20,2) THEN
    RAISE EXCEPTION 'Diskon manual melebihi batas 20%% untuk role non-admin';
  END IF;
  IF v_manual > v_subtotal THEN RAISE EXCEPTION 'Diskon manual melebihi subtotal'; END IF;

  IF v_promotion_id IS NOT NULL THEN
    SELECT * INTO v_promotion FROM public.promotions p
    WHERE p.id=v_promotion_id AND p.active
      AND (p.owner_id=v_owner OR p.owner_id IS NULL)
      AND (p.starts_at IS NULL OR p.starts_at<=now())
      AND (p.ends_at IS NULL OR p.ends_at>=now());
    IF NOT FOUND THEN RAISE EXCEPTION 'Voucher tidak tersedia atau sudah tidak berlaku'; END IF;
    IF v_promotion.scope='product' THEN
      v_base := 0;
      FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
        v_product_id := nullif(v_item->>'product_id','')::uuid;
        IF v_product_id = v_promotion.product_id THEN
          v_qty := (v_item->>'quantity')::integer;
          v_base := v_base + public.calculate_pos_item_unit_price(v_product_id, v_item) * v_qty;
        END IF;
      END LOOP;
    ELSE
      v_base := v_subtotal;
    END IF;
    IF v_promotion.scope='product' AND v_base<=0 THEN
      RAISE EXCEPTION 'Voucher hanya berlaku untuk produk tertentu';
    END IF;
    IF v_base < coalesce(v_promotion.min_purchase,0) THEN
      RAISE EXCEPTION 'Belum memenuhi minimum voucher';
    END IF;
    v_promo := CASE WHEN v_promotion.type='percentage'
      THEN least(v_base,v_base*v_promotion.value/100)
      ELSE least(v_base,v_promotion.value) END;
  END IF;

  v_discount := least(v_subtotal,v_manual+v_promo);
  v_service := CASE WHEN v_service_enabled THEN round((v_subtotal-v_discount)*v_service_rate/100) ELSE 0 END;
  v_tax := CASE WHEN v_tax_enabled THEN round(((v_subtotal-v_discount)+v_service)*v_tax_rate/100) ELSE 0 END;
  v_total := greatest((v_subtotal-v_discount)+v_service+v_tax,0);

  INSERT INTO public.orders(
    idempotency_key,source,status,table_id,customer_id,outlet_id,customer_name,notes,subtotal,discount,tax,total,
    is_paid,payment_method,payment_status,promotion_id
  ) VALUES (
    v_key,'pos','new',nullif(p_order->>'table_id','')::uuid,
    nullif(p_order->>'customer_id','')::uuid,nullif(p_order->>'outlet_id','')::uuid,
    nullif(p_order->>'customer_name',''),nullif(p_order->>'notes',''),v_subtotal,v_discount,v_service+v_tax,v_total,
    false,nullif(p_order->>'payment_method',''),'paid',v_promotion_id
  ) RETURNING * INTO v_order;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_items) LOOP
    v_product_id := nullif(v_item->>'product_id','')::uuid;
    v_qty := (v_item->>'quantity')::integer;
    SELECT * INTO v_product FROM public.products WHERE id=v_product_id AND is_active=true FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Produk tidak ditemukan atau tidak aktif'; END IF;
    IF v_product.stock < v_qty THEN
      RAISE EXCEPTION 'Stok tidak cukup untuk produk % (tersedia %, diminta %)',
        v_product.name, v_product.stock, v_qty;
    END IF;
    v_unit_price := public.calculate_pos_item_unit_price(v_product_id, v_item);
    INSERT INTO public.order_items(order_id,product_id,product_name,unit_price,quantity,note,line_total)
    VALUES(v_order.id,v_product_id,v_product.name,v_unit_price,v_qty,
      nullif(v_item->>'note',''),v_unit_price*v_qty);
    UPDATE public.products SET stock = stock - v_qty WHERE id=v_product_id;
  END LOOP;

  IF p_payment IS NOT NULL THEN
    INSERT INTO public.payments(order_id,method,amount,cash_received,change_amount)
    VALUES(
      v_order.id,coalesce(p_payment->>'method',p_order->>'payment_method'),v_total,
      nullif(p_payment->>'cash_received','')::numeric,
      CASE WHEN coalesce(p_payment->>'method',p_order->>'payment_method')='cash'
        THEN greatest(coalesce(nullif(p_payment->>'cash_received','')::numeric,v_total)-v_total,0)
        ELSE NULL END
    );
  END IF;

  UPDATE public.orders SET status='completed',is_paid=true,payment_status='paid',updated_at=now()
  WHERE id=v_order.id RETURNING * INTO v_order;
  RETURN jsonb_build_object('order',to_jsonb(v_order),'idempotent_replay',false);
END;
$function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.km_record_cash_fund_entry(p_owner_id uuid, p_entry_type text, p_amount numeric, p_source text, p_description text, p_idempotency_key uuid)
 RETURNS SETOF cash_fund_entries
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
 v_owner uuid;
 v_existing public.cash_fund_entries%rowtype;
 v_target numeric:=0;
 v_adjustments numeric:=0;
 v_expenses numeric:=0;
 v_balance numeric:=0;
 v_session_start timestamptz;
 v_last_close timestamptz;
begin
 if (select auth.uid()) is null then raise exception 'Login diperlukan.' using errcode='28000'; end if;
 if p_entry_type='close_session' then
   if not public.has_permission('shift.manage') then raise exception 'Akses tutup sesi kasir ditolak.' using errcode='42501'; end if;
 else
   if not public.has_permission('cash_fund.adjust') then raise exception 'Akses penyesuaian modal kas ditolak.' using errcode='42501'; end if;
 end if;
 v_owner:=public.km_resolve_owner_id();
 if p_owner_id is distinct from v_owner then raise exception 'Akses outlet ditolak.' using errcode='42501'; end if;
 if p_entry_type not in ('set_target','replenishment','close_session') then raise exception 'Jenis transaksi modal tidak valid.'; end if;
 if p_entry_type <> 'close_session' and (p_amount is null or p_amount<=0 or p_amount>1000000000) then raise exception 'Nominal tidak valid.'; end if;
 if nullif(trim(p_description),'') is null then raise exception 'Keterangan wajib diisi.'; end if;
 if p_source not in ('setting','sales_cash','sales_qris','sales_transfer','owner_funds','other') then raise exception 'Sumber dana tidak valid.'; end if;
 if p_idempotency_key is null then raise exception 'Kunci transaksi wajib ada.'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_owner::text||':cash',0));
 select * into v_existing from public.cash_fund_entries where owner_id=v_owner and idempotency_key=p_idempotency_key limit 1;
 if found then return next v_existing; return; end if;

 select created_at, amount into v_session_start, v_target
 from public.cash_fund_entries where owner_id=v_owner and entry_type='set_target'
 order by created_at desc, id desc limit 1;
 select created_at into v_last_close from public.cash_fund_entries
 where owner_id=v_owner and entry_type='close_session'
 order by created_at desc, id desc limit 1;

 if p_entry_type='set_target' then
   if v_session_start is not null and (v_last_close is null or v_session_start > v_last_close) then
     raise exception 'Sesi sebelumnya masih aktif. Akhiri sesi terlebih dahulu.';
   end if;
   insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by)
   values(v_owner,p_entry_type,p_amount,p_source,trim(p_description),p_idempotency_key,(select auth.uid()))
   returning * into v_existing;
   return next v_existing; return;
 end if;

 if v_session_start is null or v_target is null or v_target<=0 or (v_last_close is not null and v_last_close>v_session_start) then
   raise exception 'Tidak ada sesi aktif. Mulai sesi dan simpan kas modal terlebih dahulu.';
 end if;

 select coalesce(sum(amount),0) into v_adjustments from public.cash_fund_entries
 where owner_id=v_owner and entry_type='replenishment' and created_at>=v_session_start;
 select coalesce(sum(amount),0) into v_expenses from public.expenses
 where owner_id=v_owner and cash_source='cash_fund' and created_at>=v_session_start;
 v_balance:=greatest(v_target+v_adjustments-v_expenses,0);

 if p_entry_type='close_session' then
   insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by)
   values(v_owner,'close_session',v_balance,'setting',trim(p_description),p_idempotency_key,(select auth.uid()))
   returning * into v_existing;
   return next v_existing; return;
 end if;

 if p_amount>greatest(v_target-v_balance,0) then
   raise exception 'Adjustment melebihi kekurangan modal. Target %, saldo sekarang %, kekurangan %',v_target,v_balance,greatest(v_target-v_balance,0);
 end if;
 insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by)
 values(v_owner,p_entry_type,p_amount,p_source,trim(p_description),p_idempotency_key,(select auth.uid()))
 returning * into v_existing;
 return next v_existing;
end; $function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.km_record_operational_expense(p_owner_id uuid, p_amount numeric, p_category text, p_description text, p_expense_date date, p_payment_method text, p_cash_source text, p_idempotency_key uuid, p_shift_id uuid DEFAULT NULL::uuid)
 RETURNS SETOF expenses
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  IF NOT public.has_permission('expenses.create') THEN RAISE EXCEPTION 'Akses catat pengeluaran ditolak.' USING errcode='42501'; END IF;
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
            lower(coalesce(p.method,'')) LIKE '%qris%'
            OR lower(coalesce(p.method,'')) LIKE '%transfer%'
            OR lower(coalesce(p.method,'')) LIKE '%bank%'
            OR lower(coalesce(p.method,'')) LIKE '%bca%'
            OR lower(coalesce(p.method,'')) LIKE '%bni%'
            OR lower(coalesce(p.method,'')) LIKE '%bri%'
            OR lower(coalesce(p.method,'')) LIKE '%mandiri%'
            OR lower(coalesce(p.method,'')) LIKE '%permata%'
            OR lower(coalesce(p.method,'')) LIKE '%e_wallet%'
            OR lower(coalesce(p.method,'')) LIKE '%ewallet%'
            OR lower(coalesce(p.method,'')) LIKE '%e wallet%'
            OR lower(coalesce(p.method,'')) LIKE '%e-wallet%'
            OR lower(coalesce(p.method,'')) LIKE '%dana%'
            OR lower(coalesce(p.method,'')) LIKE '%gopay%'
            OR lower(coalesce(p.method,'')) LIKE '%go pay%'
            OR lower(coalesce(p.method,'')) LIKE '%ovo%'
            OR lower(coalesce(p.method,'')) LIKE '%shopeepay%'
            OR lower(coalesce(p.method,'')) LIKE '%linkaja%'
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
            lower(coalesce(o.payment_method,'')) LIKE '%qris%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%transfer%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bank%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bca%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bni%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%bri%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%mandiri%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%permata%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%e_wallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%ewallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%e wallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%e-wallet%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%dana%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%gopay%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%go pay%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%ovo%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%shopeepay%'
            OR lower(coalesce(o.payment_method,'')) LIKE '%linkaja%'
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
$function$
$sql$;
EXECUTE $sql$CREATE OR REPLACE FUNCTION public.reject_qr_order(p_order_id uuid)
 RETURNS orders
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_order public.orders%rowtype;
begin
  if auth.uid() is null then raise exception 'Sesi login tidak ditemukan'; end if;
  if not public.has_permission('orders.reject') then raise exception 'Akun tidak punya izin pesanan'; end if;
  select * into v_order from public.orders where id = p_order_id for update;
  if v_order.id is null then raise exception 'Pesanan tidak ditemukan'; end if;
  if v_order.source <> 'qr' or v_order.status <> 'new' then raise exception 'Pesanan sudah diproses'; end if;
  update public.orders set payment_status='rejected', status='cancelled', updated_at=now() where id=v_order.id returning * into v_order;
  return v_order;
end;
$function$
$sql$;
END;
$do$;
