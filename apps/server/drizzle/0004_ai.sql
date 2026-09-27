CREATE TABLE `latency_samples` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`task` text NOT NULL,
	`provider_kind` text NOT NULL,
	`model` text NOT NULL,
	`ok` integer NOT NULL,
	`ttft_ms` integer,
	`total_ms` integer,
	`tokens_per_sec` real,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `latency_user_task_idx` ON `latency_samples` (`user_id`,`task`);--> statement-breakpoint
CREATE TABLE `provider_configs` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` integer NOT NULL,
	`kind` text NOT NULL,
	`name` text NOT NULL,
	`base_url` text NOT NULL,
	`protocol` text NOT NULL,
	`api_key_enc` text,
	`key_hint` text,
	`created_at` integer DEFAULT (unixepoch('subsec') * 1000) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `provider_configs_user_idx` ON `provider_configs` (`user_id`);--> statement-breakpoint
CREATE TABLE `task_models` (
	`user_id` integer NOT NULL,
	`task` text NOT NULL,
	`provider_id` integer NOT NULL,
	`model` text NOT NULL,
	`reasoning` integer,
	PRIMARY KEY(`user_id`, `task`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`provider_id`) REFERENCES `provider_configs`(`id`) ON UPDATE no action ON DELETE cascade
);
