ALTER TABLE `user_settings` ADD `auto_next_correct_ms` integer DEFAULT 1300 NOT NULL;--> statement-breakpoint
ALTER TABLE `user_settings` ADD `auto_next_typo_ms` integer DEFAULT 1300 NOT NULL;--> statement-breakpoint
ALTER TABLE `user_settings` ADD `auto_next_wrong_ms` integer DEFAULT 0 NOT NULL;
