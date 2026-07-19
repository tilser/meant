insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'inventory-photos',
  'inventory-photos',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Users can read own inventory photos" on storage.objects;
drop policy if exists "Users can upload own inventory photos" on storage.objects;
drop policy if exists "Users can delete own inventory photos" on storage.objects;

create policy "Users can read own inventory photos"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'inventory-photos'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can upload own inventory photos"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'inventory-photos'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can delete own inventory photos"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'inventory-photos'
  and (storage.foldername(name))[1] = auth.uid()::text
);
