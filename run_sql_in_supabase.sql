-- Supabase SQL Script for TrackMe App
-- Run this in your Supabase SQL Editor to set up the necessary tables and policies.

-- 1. Workout Plans Table
CREATE TABLE IF NOT EXISTS public.workout_plans (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.workout_plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own workout plans" ON public.workout_plans
    FOR ALL USING (auth.uid() = user_id);

-- 2. Workout Days Table
CREATE TABLE IF NOT EXISTS public.workout_days (
    id TEXT PRIMARY KEY,
    plan_id TEXT NOT NULL REFERENCES public.workout_plans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    day_of_week TEXT NOT NULL,
    name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.workout_days ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own workout days" ON public.workout_days
    FOR ALL USING (auth.uid() = user_id);

-- 3. Planned Exercises Table
CREATE TABLE IF NOT EXISTS public.planned_exercises (
    id TEXT PRIMARY KEY,
    day_id TEXT NOT NULL REFERENCES public.workout_days(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    order_index INTEGER NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    target_sets INTEGER NOT NULL DEFAULT 3,
    target_reps INTEGER,
    target_weight_kg FLOAT,
    target_duration_seconds INTEGER,
    target_distance_km FLOAT,
    target_speed_kmh FLOAT,
    target_incline FLOAT
);

ALTER TABLE public.planned_exercises ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own planned exercises" ON public.planned_exercises
    FOR ALL USING (auth.uid() = user_id);

-- 4. Workout Sessions Table
CREATE TABLE IF NOT EXISTS public.workout_sessions (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    day_id TEXT NOT NULL REFERENCES public.workout_days(id) ON DELETE CASCADE,
    date BIGINT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    notes TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.workout_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own workout sessions" ON public.workout_sessions
    FOR ALL USING (auth.uid() = user_id);

-- 5. Session Sets Table
CREATE TABLE IF NOT EXISTS public.session_sets (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES public.workout_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    set_number INTEGER NOT NULL,
    weight_kg FLOAT NOT NULL,
    reps INTEGER NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    duration_seconds INTEGER,
    distance_km FLOAT,
    speed_kmh FLOAT,
    incline_percent FLOAT
);

ALTER TABLE public.session_sets ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own session sets" ON public.session_sets
    FOR ALL USING (auth.uid() = user_id);

-- 6. Muscle Weekly Analytics Table
CREATE TABLE IF NOT EXISTS public.muscle_weekly_analytics (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    muscle_group TEXT NOT NULL,
    week_offset INTEGER NOT NULL,
    weekly_stimulus FLOAT NOT NULL,
    growth_index FLOAT NOT NULL,
    fatigue FLOAT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.muscle_weekly_analytics ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own muscle weekly analytics" ON public.muscle_weekly_analytics
    FOR ALL USING (auth.uid() = user_id);

-- 7. Exercise Progress Snapshots Table
CREATE TABLE IF NOT EXISTS public.exercise_progress_snapshots (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    exercise_name TEXT NOT NULL,
    current_1rm FLOAT NOT NULL,
    projected_1rm_30_days FLOAT NOT NULL,
    projected_1rm_90_days FLOAT NOT NULL,
    projected_1rm_365_days FLOAT NOT NULL,
    is_plateaued BOOLEAN NOT NULL DEFAULT false,
    improvement_percentage FLOAT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.exercise_progress_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own exercise progress snapshots" ON public.exercise_progress_snapshots
    FOR ALL USING (auth.uid() = user_id);

-- 8. Daily Health Analytics Table
CREATE TABLE IF NOT EXISTS public.daily_health_analytics (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date_millis BIGINT NOT NULL,
    bmr FLOAT NOT NULL,
    calories_steps FLOAT NOT NULL,
    calories_active FLOAT NOT NULL,
    calories_lifting FLOAT NOT NULL,
    calories_tdee FLOAT NOT NULL,
    steps BIGINT NOT NULL,
    weight_kg FLOAT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.daily_health_analytics ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own daily health analytics" ON public.daily_health_analytics
    FOR ALL USING (auth.uid() = user_id);

-- 9. Body State Snapshots Table
CREATE TABLE IF NOT EXISTS public.body_state_snapshots (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    consistency_score FLOAT NOT NULL,
    readiness_score INTEGER NOT NULL,
    muscle_balance_push_pull FLOAT NOT NULL,
    muscle_balance_quad_ham FLOAT NOT NULL,
    muscle_balance_upper_lower FLOAT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

ALTER TABLE public.body_state_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own body state snapshots" ON public.body_state_snapshots
    FOR ALL USING (auth.uid() = user_id);

-- 10. Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_workout_plans_user ON public.workout_plans(user_id);
CREATE INDEX IF NOT EXISTS idx_workout_days_user ON public.workout_days(user_id);
CREATE INDEX IF NOT EXISTS idx_planned_exercises_user ON public.planned_exercises(user_id);
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user ON public.workout_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_session_sets_user ON public.session_sets(user_id);
CREATE INDEX IF NOT EXISTS idx_daily_health_analytics_user ON public.daily_health_analytics(user_id);

-- 11. User Sessions Table (Single Active Session Management)
CREATE TABLE IF NOT EXISTS public.user_sessions (
    device_id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('active', 'sync_requested', 'sync_completed', 'terminated')),
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own sessions" ON public.user_sessions
    FOR ALL USING (auth.uid() = user_id);

-- 12. RPC Functions for Session Management

-- RPC: Register Session
CREATE OR REPLACE FUNCTION public.register_session(device_id_param TEXT)
RETURNS VOID AS $$
DECLARE
    caller_uid UUID;
BEGIN
    caller_uid := auth.uid();
    IF caller_uid IS NULL THEN
        RAISE EXCEPTION 'Not authorized';
    END IF;

    -- 1. Mark other active sessions for this user as sync_requested or terminated
    UPDATE public.user_sessions
    SET status = CASE 
        WHEN last_active_at >= now() - INTERVAL '45 seconds' THEN 'sync_requested'::text
        ELSE 'terminated'::text
    END,
    updated_at = now()
    WHERE user_id = caller_uid AND device_id != device_id_param AND status = 'active';

    -- 2. Upsert current session as active
    INSERT INTO public.user_sessions (device_id, user_id, status, last_active_at, updated_at)
    VALUES (device_id_param, caller_uid, 'active', now(), now())
    ON CONFLICT (device_id) DO UPDATE
    SET status = 'active', last_active_at = now(), updated_at = now();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: Heartbeat Session
CREATE OR REPLACE FUNCTION public.heartbeat_session(device_id_param TEXT)
RETURNS TEXT AS $$
DECLARE
    caller_uid UUID;
    session_status TEXT;
BEGIN
    caller_uid := auth.uid();
    IF caller_uid IS NULL THEN
        RETURN 'terminated';
    END IF;

    -- Update last active and fetch status
    UPDATE public.user_sessions
    SET last_active_at = now()
    WHERE device_id = device_id_param AND user_id = caller_uid
    RETURNING status INTO session_status;

    IF session_status IS NULL THEN
        RETURN 'terminated';
    END IF;

    RETURN session_status;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: Confirm Sync Complete
CREATE OR REPLACE FUNCTION public.confirm_sync_complete()
RETURNS VOID AS $$
DECLARE
    caller_uid UUID;
BEGIN
    caller_uid := auth.uid();
    IF caller_uid IS NULL THEN
        RAISE EXCEPTION 'Not authorized';
    END IF;

    -- Mark sync_requested sessions as terminated after they've synced
    UPDATE public.user_sessions
    SET status = 'terminated', updated_at = now()
    WHERE user_id = caller_uid AND status = 'sync_requested';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 13. User FCM Tokens Table (Push Notification Targets)
CREATE TABLE IF NOT EXISTS public.user_fcm_tokens (
    fcm_token TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE public.user_fcm_tokens ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own FCM tokens" ON public.user_fcm_tokens
    FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_user_fcm_tokens_user ON public.user_fcm_tokens(user_id);
