CREATE TABLE `user_settings` (
	`user_id` integer PRIMARY KEY NOT NULL,
	`intervals` text NOT NULL,
	`wrong_mode` text NOT NULL,
	`ai_timeout_ms` integer NOT NULL,
	`reminder_time` text,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
