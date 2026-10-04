CREATE TABLE interview_sessions (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL CHECK(version>=0), direction TEXT NOT NULL CHECK(direction IN ('JAVA_BACKEND','REACT_FRONTEND','AGENT_DEVELOPMENT','FULL_STACK')),
    difficulty TEXT NOT NULL CHECK(difficulty IN ('JUNIOR','MID','SENIOR')), main_count INT NOT NULL CHECK(main_count BETWEEN 3 AND 20),
    generation_status TEXT NOT NULL CHECK(generation_status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
    answer_status TEXT NOT NULL CHECK(answer_status IN ('NOT_READY','READY','IN_PROGRESS','COMPLETED')),
    evaluation_status TEXT NOT NULL CHECK(evaluation_status IN ('NOT_STARTED','PENDING','PROCESSING','SUCCEEDED','FAILED')),
    current_turn INT NOT NULL CHECK(current_turn>=0 AND current_turn<=main_count*2), submitted_count INT NOT NULL CHECK(submitted_count BETWEEN 0 AND main_count*2),
    resume_text TEXT CHECK(CHAR_LENGTH(resume_text)<=20000), resume_version BIGINT, resume_hash TEXT,
    jd_text TEXT CHECK(CHAR_LENGTH(jd_text)<=10000), jd_result TEXT, input_hash TEXT NOT NULL,
    model_version TEXT NOT NULL, question_version TEXT NOT NULL, rubric_version TEXT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, total_score NUMERIC(5,2) CHECK(total_score BETWEEN 0 AND 100), overall_feedback TEXT, safe_failure_code TEXT,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, UNIQUE(user_id,id),
    CHECK ((resume_text IS NULL AND resume_version IS NULL AND resume_hash IS NULL) OR (resume_text IS NOT NULL AND resume_version IS NOT NULL AND resume_hash IS NOT NULL)),
    CHECK (total_score IS NULL OR evaluation_status='SUCCEEDED'),
    CHECK (NOT deleted OR (resume_text IS NULL AND jd_text IS NULL AND jd_result IS NULL AND overall_feedback IS NULL AND total_score IS NULL))
);
CREATE TABLE interview_questions (
    session_id UUID NOT NULL,user_id UUID NOT NULL,turn_index INT NOT NULL CHECK(turn_index BETWEEN 0 AND 39),
    type TEXT NOT NULL CHECK(type IN ('MAIN','FOLLOW_UP')),parent_main_index INT,text TEXT NOT NULL CHECK(length(btrim(text))>0),
    PRIMARY KEY(session_id,turn_index),FOREIGN KEY(user_id,session_id) REFERENCES interview_sessions(user_id,id) ON DELETE RESTRICT,
    CHECK ((type='MAIN' AND turn_index%2=0 AND parent_main_index IS NULL) OR (type='FOLLOW_UP' AND turn_index%2=1 AND parent_main_index=turn_index-1))
);
CREATE TABLE interview_answers (
    session_id UUID NOT NULL,user_id UUID NOT NULL,turn_index INT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','UNANSWERED')),answer_text TEXT NOT NULL CHECK(CHAR_LENGTH(answer_text)<=5000),version BIGINT NOT NULL CHECK(version>=0),
    PRIMARY KEY(session_id,turn_index),FOREIGN KEY(user_id,session_id) REFERENCES interview_sessions(user_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(session_id,turn_index) REFERENCES interview_questions(session_id,turn_index) ON DELETE RESTRICT
);
CREATE TABLE interview_jd_analyses (
    id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,version BIGINT NOT NULL CHECK(version>=0),
    direction TEXT NOT NULL CHECK(direction IN ('JAVA_BACKEND','REACT_FRONTEND','AGENT_DEVELOPMENT','FULL_STACK')),
    jd_text TEXT CHECK(CHAR_LENGTH(jd_text)<=10000),raw_hash TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
    result_json TEXT,deleted BOOLEAN NOT NULL DEFAULT FALSE,safe_failure_code TEXT,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id,id),CHECK(NOT deleted OR (jd_text IS NULL AND result_json IS NULL))
);
CREATE TABLE interview_ai_jobs (
    id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    kind TEXT NOT NULL CHECK(kind IN ('JD','GENERATE','EVALUATE')),session_id UUID,jd_id UUID,main_index INT,
    input_hash TEXT NOT NULL,payload_json TEXT,status TEXT NOT NULL CHECK(status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
    token UUID,lease_expires_at TIMESTAMPTZ,queue_deadline TIMESTAMPTZ NOT NULL,safe_failure_code TEXT,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(user_id,session_id) REFERENCES interview_sessions(user_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(user_id,jd_id) REFERENCES interview_jd_analyses(user_id,id) ON DELETE RESTRICT,
    UNIQUE(user_id,id),CHECK((status='PROCESSING' AND token IS NOT NULL AND lease_expires_at IS NOT NULL) OR status<>'PROCESSING'),
    CHECK((kind='JD' AND jd_id IS NOT NULL AND session_id IS NULL AND main_index IS NULL) OR (kind='GENERATE' AND session_id IS NOT NULL AND jd_id IS NULL AND main_index IS NULL)
        OR (kind='EVALUATE' AND session_id IS NOT NULL AND jd_id IS NULL AND main_index IS NOT NULL AND main_index BETWEEN 0 AND 38 AND main_index%2=0))
);
CREATE TABLE interview_evaluations (
    session_id UUID NOT NULL,user_id UUID NOT NULL,main_index INT NOT NULL CHECK(main_index BETWEEN 0 AND 38 AND main_index%2=0),
    input_hash TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),result_json TEXT,safe_failure_code TEXT,
    PRIMARY KEY(session_id,main_index),FOREIGN KEY(user_id,session_id) REFERENCES interview_sessions(user_id,id) ON DELETE RESTRICT,
    CHECK(status<>'SUCCEEDED' OR result_json IS NOT NULL)
);
CREATE TABLE interview_operation_receipts (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,operation TEXT NOT NULL,request_id UUID NOT NULL,payload_hash TEXT NOT NULL,
    session_id UUID,jd_id UUID,state TEXT NOT NULL,result_version BIGINT NOT NULL CHECK(result_version>=0),turn_index INT,created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(user_id,operation,request_id),FOREIGN KEY(user_id,session_id) REFERENCES interview_sessions(user_id,id) ON DELETE RESTRICT,
    FOREIGN KEY(user_id,jd_id) REFERENCES interview_jd_analyses(user_id,id) ON DELETE RESTRICT,
    CHECK((session_id IS NULL)<>(jd_id IS NULL))
);
CREATE INDEX ix_interview_sessions_owner_page ON interview_sessions(user_id,created_at DESC,id DESC) WHERE NOT deleted;
CREATE INDEX ix_interview_jobs_expiry ON interview_ai_jobs(status,queue_deadline,lease_expires_at);
