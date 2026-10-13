-- Jalankan sekali di Supabase SQL Editor untuk mengaktifkan foto produk.
-- Bucket publik hanya untuk membaca gambar; upload hanya oleh user yang login.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('product-images', 'product-images', true, 8388608, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public = true, file_size_limit = 8388608,
    allowed_mime_types = array['image/jpeg','image/png','image/webp'];

drop policy if exists "Authenticated staff upload product images" on storage.objects;
create policy "Authenticated staff upload product images"
on storage.objects for insert to authenticated
with check (bucket_id = 'product-images');

drop policy if exists "Authenticated staff update product images" on storage.objects;
create policy "Authenticated staff update product images"
on storage.objects for update to authenticated
using (bucket_id = 'product-images')
with check (bucket_id = 'product-images');

drop policy if exists "Public read product images" on storage.objects;
create policy "Public read product images"
on storage.objects for select to public
using (bucket_id = 'product-images');
