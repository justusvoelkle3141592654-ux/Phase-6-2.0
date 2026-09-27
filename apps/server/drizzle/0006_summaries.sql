CREATE TABLE `daily_summaries` (
	`user_id` integer NOT NULL,
	`day` text NOT NULL,
	`attempts` integer NOT NULL,
	`text` text NOT NULL,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	PRIMARY KEY(`user_id`, `day`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
