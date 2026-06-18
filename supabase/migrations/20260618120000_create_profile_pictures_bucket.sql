insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-pictures',
  'profile-pictures',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Users can read own profile pictures" on storage.objects;
drop policy if exists "Users can upload own profile pictures" on storage.objects;
drop policy if exists "Users can delete own profile pictures" on storage.objects;

create policy "Users can read own profile pictures"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'profile-pictures'
  and (storage.foldername(name))[1] = (select auth.jwt() ->> 'sub')
);

create policy "Users can upload own profile pictures"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'profile-pictures'
  and (storage.foldername(name))[1] = (select auth.jwt() ->> 'sub')
);

create policy "Users can delete own profile pictures"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'profile-pictures'
  and (storage.foldername(name))[1] = (select auth.jwt() ->> 'sub')
);
