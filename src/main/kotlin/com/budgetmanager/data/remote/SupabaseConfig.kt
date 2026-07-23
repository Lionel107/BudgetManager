package com.budgetmanager.data.remote

import java.util.Properties

/**
 * Charge l'URL du projet Supabase et la anon key depuis le classpath
 * (`src/main/resources/supabase.properties`, ignoré par git).
 *
 * Les variables d'environnement `SUPABASE_URL` / `SUPABASE_ANON_KEY` ont
 * priorité si elles sont définies (pratique pour CI ou surcharge locale).
 */
object SupabaseConfig {

    val url: String
    val anonKey: String

    init {
        val props = Properties()
        SupabaseConfig::class.java.classLoader
            .getResourceAsStream("supabase.properties")
            ?.use { props.load(it) }

        url = System.getenv("SUPABASE_URL")
            ?: props.getProperty("SUPABASE_URL")
            ?: error(
                "Configuration Supabase manquante : crée src/main/resources/supabase.properties " +
                    "(voir supabase.properties.example) ou définis la variable d'env SUPABASE_URL."
            )
        anonKey = System.getenv("SUPABASE_ANON_KEY")
            ?: props.getProperty("SUPABASE_ANON_KEY")
            ?: error(
                "Configuration Supabase manquante : SUPABASE_ANON_KEY absent " +
                    "(supabase.properties ou variable d'environnement)."
            )
    }
}
