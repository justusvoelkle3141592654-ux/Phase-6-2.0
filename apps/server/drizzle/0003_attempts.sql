CREATE TABLE `accepted_answers` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`vocab_id` integer NOT NULL,
	`direction` text NOT NULL,
	`answer` text NOT NULL,
	`verdict` text NOT NULL,
	`source` text NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`vocab_id`) REFERENCES `vocab`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `accepted_unique` ON `accepted_answers` (`vocab_id`,`direction`,`answer`);--> statement-breakpoint
CREATE TABLE `attempts` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`vocab_id` integer NOT NULL,
	`direction` text NOT NULL,
	`answer` text,
	`correct` integer NOT NULL,
	`decided_by` text NOT NULL,
	`ms_to_first_key` integer,
	`ms_total` integer,
	`stage_before` integer NOT NULL,
	`stage_after` integer NOT NULL,
	`repeat` integer DEFAULT false NOT NULL,
	`day` text NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`vocab_id`) REFERENCES `vocab`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `attempts_user_day_idx` ON `attempts` (`user_id`,`day`);--> statement-breakpoint
CREATE INDEX `attempts_vocab_idx` ON `attempts` (`vocab_id`);