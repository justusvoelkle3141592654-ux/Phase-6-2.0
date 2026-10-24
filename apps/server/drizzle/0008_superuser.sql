ALTER TABLE `users` ADD `role` text DEFAULT 'user' NOT NULL;--> statement-breakpoint
CREATE TABLE `user_vocab` (
	`user_id` integer NOT NULL,
	`vocab_id` integer NOT NULL,
	`stage` integer DEFAULT 1 NOT NULL,
	`due_date` text,
	`learned` integer DEFAULT false NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	PRIMARY KEY(`user_id`, `vocab_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`vocab_id`) REFERENCES `vocab`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `user_vocab` (`user_id`, `vocab_id`, `stage`, `due_date`, `learned`, `created_at`)
	SELECT `user_id`, `id`, `stage`, `due_date`, `learned`, `created_at` FROM `vocab` WHERE `active` = 1;--> statement-breakpoint
CREATE INDEX `user_vocab_due_idx` ON `user_vocab` (`user_id`,`due_date`);--> statement-breakpoint
CREATE INDEX `user_vocab_vocab_idx` ON `user_vocab` (`vocab_id`);--> statement-breakpoint
CREATE TABLE `__new_packages` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`name` text NOT NULL,
	`language` text NOT NULL,
	`direction` text DEFAULT 'foreign_native' NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL
);
--> statement-breakpoint
INSERT INTO `__new_packages` (`id`, `name`, `language`, `direction`, `created_at`)
	SELECT `id`, `name`, `language`, `direction`, `created_at` FROM `packages`;--> statement-breakpoint
DROP TABLE `packages`;--> statement-breakpoint
ALTER TABLE `__new_packages` RENAME TO `packages`;--> statement-breakpoint
CREATE TABLE `__new_vocab` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`package_id` integer NOT NULL,
	`word` text NOT NULL,
	`extra` text DEFAULT '' NOT NULL,
	`translation` text NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`package_id`) REFERENCES `packages`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `__new_vocab` (`id`, `package_id`, `word`, `extra`, `translation`, `created_at`)
	SELECT `id`, `package_id`, `word`, `extra`, `translation`, `created_at` FROM `vocab`;--> statement-breakpoint
DROP TABLE `vocab`;--> statement-breakpoint
ALTER TABLE `__new_vocab` RENAME TO `vocab`;--> statement-breakpoint
CREATE INDEX `vocab_package_idx` ON `vocab` (`package_id`);--> statement-breakpoint
DROP TABLE `daily_summaries`;--> statement-breakpoint
DELETE FROM `task_models` WHERE `task` = 'summary';--> statement-breakpoint
DELETE FROM `latency_samples` WHERE `task` = 'summary';
