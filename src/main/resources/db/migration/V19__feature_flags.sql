-- On/off switches the admin can flip from App management to show or hide a feature in the web and Android apps
-- without a release (FeatureFlagService). Value true or false; a missing switch counts as off. Chat starts on, as it
-- is today; a switch an admin already created is left as it is.
INSERT INTO app_parameters (param_key, param_value, description, created_at, updated_at) VALUES
    ('feature.chat.enabled', 'true', 'Chat (messages) in the web and Android apps: true shows it, false hides it and the chat endpoints answer 403', now(), now())
ON CONFLICT (param_key) DO NOTHING;
