-- ============================================================================
-- BudgetManager — Fonctions RPC (tags + divers), opérations atomiques
-- SECURITY INVOKER : la RLS s'applique (chacun ne touche que ses données).
-- ============================================================================

-- Incrément atomique du compteur d'usage d'un modèle (template).
create or replace function public.increment_template_usage(p_id bigint)
returns void
language sql
security invoker
as $$
    update public.templates set usage_count = usage_count + 1 where id = p_id;
$$;

-- Récupère l'id d'un tag par nom (normalisé), le crée s'il n'existe pas.
create or replace function public.get_or_create_tag(p_name text)
returns bigint
language plpgsql
security invoker
as $$
declare
    v_name text := lower(trim(p_name));
    v_id   bigint;
begin
    if v_name = '' then
        return -1;
    end if;
    select id into v_id from public.tags where user_id = auth.uid() and name = v_name;
    if v_id is null then
        insert into public.tags (name, usage_count) values (v_name, 0) returning id into v_id;
    end if;
    return v_id;
end;
$$;

-- Remplace tous les tags d'une transaction et recalcule usage_count des tags affectés.
create or replace function public.set_transaction_tags(
    p_transaction_id bigint,
    p_tag_ids bigint[]
)
returns void
language plpgsql
security invoker
as $$
declare
    v_previous bigint[];
    v_affected bigint[];
    v_tag_id   bigint;
begin
    select coalesce(array_agg(tag_id), '{}') into v_previous
        from public.transaction_tags where transaction_id = p_transaction_id;

    delete from public.transaction_tags where transaction_id = p_transaction_id;

    if p_tag_ids is not null then
        foreach v_tag_id in array p_tag_ids loop
            insert into public.transaction_tags (transaction_id, tag_id)
                values (p_transaction_id, v_tag_id)
                on conflict do nothing;
        end loop;
    end if;

    v_affected := array(select distinct unnest(v_previous || coalesce(p_tag_ids, '{}')));
    foreach v_tag_id in array v_affected loop
        update public.tags
        set usage_count = (select count(*) from public.transaction_tags where tag_id = v_tag_id)
        where id = v_tag_id;
    end loop;
end;
$$;

-- Suggère des tags issus de transactions dont le titre contient un des tokens.
create or replace function public.suggest_tags_for_title(
    p_tokens text[],
    p_limit int default 5
)
returns setof public.tags
language sql
security invoker
as $$
    select tg.*
    from public.tags tg
    join public.transaction_tags tt on tt.tag_id = tg.id
    join public.transactions t on t.id = tt.transaction_id
    where exists (
        select 1 from unnest(p_tokens) tok where lower(t.title) like '%' || tok || '%'
    )
    group by tg.id
    order by count(*) desc, tg.usage_count desc
    limit p_limit;
$$;
