CREATE TABLE `__new_packages` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`name` text NOT NULL,
	`language` text NOT NULL,
	`direction` text DEFAULT 'foreign_native' NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	`src_id` integer,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `__new_packages` (`id`, `user_id`, `name`, `language`, `direction`, `created_at`)
	SELECT `id`,
		COALESCE((SELECT `id` FROM `users` WHERE `role` = 'admin' ORDER BY `id` LIMIT 1), (SELECT MIN(`id`) FROM `users`)),
		`name`, `language`, `direction`, `created_at`
	FROM `packages`;--> statement-breakpoint
INSERT INTO `__new_packages` (`user_id`, `name`, `language`, `direction`, `created_at`, `src_id`)
	SELECT DISTINCT uv.`user_id`, p.`name`, p.`language`, p.`direction`, p.`created_at`, p.`id`
	FROM `user_vocab` uv
	JOIN `vocab` v ON v.`id` = uv.`vocab_id`
	JOIN `packages` p ON p.`id` = v.`package_id`
	JOIN `users` u ON u.`id` = uv.`user_id`
	WHERE u.`role` != 'admin'
	ORDER BY uv.`user_id`, p.`id`;--> statement-breakpoint
ALTER TABLE `vocab` ADD `src_vocab_id` integer;--> statement-breakpoint
INSERT INTO `vocab` (`package_id`, `word`, `extra`, `translation`, `created_at`, `src_vocab_id`)
	SELECT np.`id`, v.`word`, v.`extra`, v.`translation`, v.`created_at`, v.`id`
	FROM `vocab` v
	JOIN `__new_packages` np ON np.`src_id` = v.`package_id`
	ORDER BY np.`id`, v.`id`;--> statement-breakpoint
UPDATE `user_vocab` SET `vocab_id` = (
		SELECT nv.`id` FROM `vocab` nv
		JOIN `__new_packages` np ON np.`id` = nv.`package_id`
		WHERE nv.`src_vocab_id` = `user_vocab`.`vocab_id` AND np.`user_id` = `user_vocab`.`user_id`
	)
	WHERE EXISTS (
		SELECT 1 FROM `vocab` nv
		JOIN `__new_packages` np ON np.`id` = nv.`package_id`
		WHERE nv.`src_vocab_id` = `user_vocab`.`vocab_id` AND np.`user_id` = `user_vocab`.`user_id`
	);--> statement-breakpoint
UPDATE `attempts` SET `vocab_id` = (
		SELECT nv.`id` FROM `vocab` nv
		JOIN `__new_packages` np ON np.`id` = nv.`package_id`
		WHERE nv.`src_vocab_id` = `attempts`.`vocab_id` AND np.`user_id` = `attempts`.`user_id`
	)
	WHERE EXISTS (
		SELECT 1 FROM `vocab` nv
		JOIN `__new_packages` np ON np.`id` = nv.`package_id`
		WHERE nv.`src_vocab_id` = `attempts`.`vocab_id` AND np.`user_id` = `attempts`.`user_id`
	);--> statement-breakpoint
INSERT INTO `accepted_answers` (`vocab_id`, `direction`, `answer`, `verdict`, `source`, `created_at`)
	SELECT nv.`id`, a.`direction`, a.`answer`, a.`verdict`, a.`source`, a.`created_at`
	FROM `accepted_answers` a
	JOIN `vocab` nv ON nv.`src_vocab_id` = a.`vocab_id`;--> statement-breakpoint
ALTER TABLE `vocab` DROP COLUMN `src_vocab_id`;--> statement-breakpoint
DROP TABLE `packages`;--> statement-breakpoint
ALTER TABLE `__new_packages` RENAME TO `packages`;--> statement-breakpoint
ALTER TABLE `packages` DROP COLUMN `src_id`;--> statement-breakpoint
CREATE INDEX `packages_user_idx` ON `packages` (`user_id`);--> statement-breakpoint
UPDATE `user_settings` SET
	`auto_next_correct_ms` = CASE WHEN `auto_next_correct_ms` = 1300 THEN 3000 ELSE MIN(10000, CAST(ROUND(`auto_next_correct_ms` / 1000.0) AS integer) * 1000) END,
	`auto_next_typo_ms` = CASE WHEN `auto_next_typo_ms` = 1300 THEN 3000 ELSE MIN(10000, CAST(ROUND(`auto_next_typo_ms` / 1000.0) AS integer) * 1000) END,
	`auto_next_wrong_ms` = CASE WHEN `auto_next_wrong_ms` = 0 THEN 3000 ELSE MIN(10000, CAST(ROUND(`auto_next_wrong_ms` / 1000.0) AS integer) * 1000) END,
	`wrong_mode` = 'back';--> statement-breakpoint
ALTER TABLE `user_settings` ADD `daily_limit` integer DEFAULT 30 NOT NULL;--> statement-breakpoint
ALTER TABLE `user_settings` DROP COLUMN `reminder_time`;--> statement-breakpoint
ALTER TABLE `users` DROP COLUMN `setup_completed`;
