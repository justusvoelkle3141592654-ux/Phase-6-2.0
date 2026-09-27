CREATE TABLE `packages` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`name` text NOT NULL,
	`language` text NOT NULL,
	`direction` text DEFAULT 'foreign_native' NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `packages_user_idx` ON `packages` (`user_id`);--> statement-breakpoint
CREATE TABLE `vocab` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`package_id` integer NOT NULL,
	`word` text NOT NULL,
	`extra` text DEFAULT '' NOT NULL,
	`translation` text NOT NULL,
	`active` integer DEFAULT false NOT NULL,
	`stage` integer DEFAULT 1 NOT NULL,
	`due_date` text,
	`learned` integer DEFAULT false NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`package_id`) REFERENCES `packages`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `vocab_package_idx` ON `vocab` (`package_id`);--> statement-breakpoint
CREATE INDEX `vocab_user_due_idx` ON `vocab` (`user_id`,`due_date`);