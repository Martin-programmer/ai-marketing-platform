ALTER TABLE ai_prompt_log ADD COLUMN IF NOT EXISTS input_text  text NULL;
ALTER TABLE ai_prompt_log ADD COLUMN IF NOT EXISTS output_text text NULL;
