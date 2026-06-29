/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.backend.room.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migration from version 1 -> 2: change category.length and category.climb from REAL/float to INTEGER
// Strategy: create new table category_new with INTEGER columns, copy data casting floats to integers (truncation),
// drop old table, rename new table back to category, recreate index.

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) create new table with desired schema (match EventDatabase_Impl.createAllTables)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `category_new` (
                `id` BLOB NOT NULL,
                `race_id` BLOB NOT NULL,
                `name` TEXT NOT NULL,
                `is_man` INTEGER NOT NULL,
                `max_age` INTEGER,
                `length` INTEGER NOT NULL,
                `climb` INTEGER NOT NULL,
                `order` INTEGER NOT NULL,
                `different_properties` INTEGER NOT NULL,
                `race_type` TEXT,
                `category_band` TEXT,
                `limit` TEXT,
                `control_points_string` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`race_id`) REFERENCES `race`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent()
        )

        // 2) copy data from old table, converting length/climb using CAST (truncates toward zero)
        db.execSQL(
            """
            INSERT INTO category_new (id, race_id, name, is_man, max_age, length, climb, `order`, different_properties, race_type, category_band, `limit`, control_points_string)
            SELECT id, race_id, name, is_man, max_age,
                   CAST(length AS INTEGER),
                   CAST(climb AS INTEGER),
                   `order`, different_properties, race_type, category_band, `limit`, control_points_string
            FROM category
        """.trimIndent()
        )

        // 3) drop old table, rename new to original name
        db.execSQL("DROP TABLE category")
        db.execSQL("ALTER TABLE category_new RENAME TO category")

        // 4) recreate indices expected by Room
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name_race_id` ON `category` (`name`, `race_id`)")

        // 5) add `interval` column to result_service (Duration stored as text). Use default 'PT10S' for existing rows.
        db.execSQL("ALTER TABLE result_service ADD COLUMN `interval` TEXT NOT NULL DEFAULT 'PT10S'")
    }
}

// Migration from version 2 -> 3: drop orig_* columns from `result` table by recreating it without those columns
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table matching the Result entity schema (without orig_check_time, orig_start_time, orig_finish_time)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `result_new` (
                `id` BLOB NOT NULL,
                `race_id` BLOB NOT NULL,
                `competitor_id` BLOB,
                `si_number` INTEGER,
                `card_type` INTEGER NOT NULL,
                `check_time` TEXT,
                `start_time` TEXT,
                `finish_time` TEXT,
                `readout_time` TEXT NOT NULL,
                `automatic_status` INTEGER NOT NULL,
                `result_status` TEXT NOT NULL,
                `points` INTEGER NOT NULL,
                `run_time` TEXT NOT NULL,
                `modified` INTEGER NOT NULL,
                `sent` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`race_id`) REFERENCES `race`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`competitor_id`) REFERENCES `competitor`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent()
        )

        // Copy data from old table into the new table skipping orig_* columns
        db.execSQL(
            """
            INSERT INTO result_new (id, race_id, competitor_id, si_number, card_type, check_time, start_time, finish_time, readout_time, automatic_status, result_status, points, run_time, modified, sent)
            SELECT id, race_id, competitor_id, si_number, card_type, check_time, start_time, finish_time, readout_time, automatic_status, result_status, points, run_time, modified, sent
            FROM result
        """.trimIndent()
        )

        // Replace old table with the new one
        db.execSQL("DROP TABLE result")
        db.execSQL("ALTER TABLE result_new RENAME TO result")

        // 6) add `init` column to result_service (boolean flag stored as INTEGER). Default 0 for existing rows.
        db.execSQL("ALTER TABLE result_service ADD COLUMN `init` INTEGER NOT NULL DEFAULT 0")
    }
}

// Migration from version 3 -> 4: preserve optional SI-card personal name captured during readout.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `result` ADD COLUMN `card_name` TEXT")
    }
}

// Migration from version 4 -> 5: add indices for foreign-key child columns so Room does not
// need full table scans when parent rows are updated or deleted.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_race_id` ON `category` (`race_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_alias_race_id` ON `alias` (`race_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_competitor_race_id` ON `competitor` (`race_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_competitor_category_id` ON `competitor` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_control_point_category_id` ON `control_point` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_race_id` ON `result` (`race_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_competitor_id` ON `result` (`competitor_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_punch_result_id` ON `punch` (`result_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_service_race_id` ON `result_service` (`race_id`)")
    }
}

// Migration from version 5 -> 6: remember imported source identity so repeated imports replace
// the previous Android copy instead of accumulating duplicate events.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `race` ADD COLUMN `import_source_id` TEXT")
        db.execSQL("ALTER TABLE `race` ADD COLUMN `import_fingerprint` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_race_import_source_id` ON `race` (`import_source_id`)")
    }
}

// Migration from version 6 -> 7: start numbers are event-local start order
// slots, so simultaneous starters may share one. Keep the lookup index but
// remove the uniqueness constraint that treated start numbers as competitors.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_competitor_start_number_race_id`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_competitor_start_number_race_id` ON `competitor` (`start_number`, `race_id`)")
    }
}

// Migration from version 7 -> 8: store Android-local Event Series membership.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_series` (
                `series_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                PRIMARY KEY(`series_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_series_member` (
                `series_id` TEXT NOT NULL,
                `series_event_id` TEXT NOT NULL,
                `local_race_id` BLOB NOT NULL,
                `event_file_path` TEXT NOT NULL,
                `event_order` INTEGER NOT NULL,
                `display_name` TEXT NOT NULL,
                `start_date_time_iso` TEXT NOT NULL,
                `format_label` TEXT NOT NULL,
                PRIMARY KEY(`series_id`, `series_event_id`),
                FOREIGN KEY(`series_id`) REFERENCES `event_series`(`series_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`local_race_id`) REFERENCES `race`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_series_member_series_id` ON `event_series_member` (`series_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_series_member_local_race_id` ON `event_series_member` (`local_race_id`)")
    }
}

// Migration from version 8 -> 9: store bib numbers separately from person/registration IDs.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `competitor` ADD COLUMN `bib_number` TEXT NOT NULL DEFAULT ''")
    }
}
