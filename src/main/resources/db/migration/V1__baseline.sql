-- Baseline: the production schema on 2026-11-13, plus reference data (roles, permissions, service catalog).
-- Existing databases skip this file (Flyway baselines them at version 1); new databases start from it.
-- Every schema change from now on is a new V<n>__<description>.sql file. Never edit an applied migration.

CREATE TABLE public.backup_codes (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    code_hash character varying(255) NOT NULL,
    used_at timestamp(6) without time zone,
    user_id character varying(255) NOT NULL
);

CREATE TABLE public.billing_charges (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    amount numeric(19,4) NOT NULL,
    billing_event character varying(30) NOT NULL,
    billing_tier_code character varying(50) NOT NULL,
    chargeable_at timestamp(6) without time zone NOT NULL,
    charged_at timestamp(6) without time zone,
    currency character varying(10) NOT NULL,
    decision_reason text,
    status character varying(20) NOT NULL,
    unit_price numeric(19,4) NOT NULL,
    billing_profile_id character varying(255) NOT NULL,
    company_id character varying(255) NOT NULL,
    invoice_id character varying(255),
    verification_request_id character varying(255) NOT NULL,
    CONSTRAINT billing_charges_billing_event_check CHECK (((billing_event)::text = ANY ((ARRAY['SUBMISSION'::character varying, 'COMPLETION'::character varying, 'REJECTION'::character varying, 'FAILURE_NON_SYSTEM'::character varying, 'FAILURE_SYSTEM'::character varying])::text[]))),
    CONSTRAINT billing_charges_status_check CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'WAIVED'::character varying, 'DEBITED'::character varying, 'INVOICED'::character varying])::text[])))
);

CREATE TABLE public.billing_invoice_lines (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    amount numeric(19,4) NOT NULL,
    description text NOT NULL,
    line_type character varying(50) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    unit_price numeric(19,4) NOT NULL,
    invoice_id character varying(255) NOT NULL
);

CREATE TABLE public.billing_invoices (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    amount_paid numeric(19,4) NOT NULL,
    billing_mode character varying(20) NOT NULL,
    currency character varying(10) NOT NULL,
    due_at date,
    generated_at timestamp(6) without time zone,
    invoice_number character varying(255) NOT NULL,
    period_end date NOT NULL,
    period_start date NOT NULL,
    recipient_email character varying(255),
    sent_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    subtotal numeric(19,4) NOT NULL,
    tax numeric(19,4) NOT NULL,
    total numeric(19,4) NOT NULL,
    billing_profile_id character varying(255) NOT NULL,
    company_id character varying(255) NOT NULL,
    CONSTRAINT billing_invoices_billing_mode_check CHECK (((billing_mode)::text = ANY ((ARRAY['PREPAID'::character varying, 'POSTPAID'::character varying])::text[]))),
    CONSTRAINT billing_invoices_status_check CHECK (((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'SENT'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.company_application (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    application_number character varying(50) NOT NULL,
    application_status character varying(255),
    business_type character varying(255),
    contact_person_email character varying(255),
    contact_person_name character varying(255),
    contact_person_phone character varying(50),
    contact_person_title character varying(100),
    country character varying(2),
    email character varying(255) NOT NULL,
    legal_name character varying(255) NOT NULL,
    phone character varying(255),
    registration_number character varying(255),
    trading_name character varying(255),
    website character varying(255),
    CONSTRAINT company_application_application_status_check CHECK (((application_status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.company_application_approvals (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    action_approved_at timestamp(6) without time zone,
    action_performed_at timestamp(6) without time zone,
    application_id character varying(255) NOT NULL,
    approved_by character varying(255),
    approver_note text,
    company_id character varying(255),
    end_user_message text,
    final_action character varying(255),
    performed_by character varying(255) NOT NULL,
    performed_by_action character varying(255) NOT NULL,
    CONSTRAINT company_application_approvals_final_action_check CHECK (((final_action)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT company_application_approvals_performed_by_action_check CHECK (((performed_by_action)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.company_billing_profiles (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    bill_on_completed boolean NOT NULL,
    bill_on_failed_non_system boolean NOT NULL,
    bill_on_failed_system boolean NOT NULL,
    bill_on_rejected boolean NOT NULL,
    bill_on_submission boolean NOT NULL,
    billing_email character varying(255),
    billing_mode character varying(20) NOT NULL,
    billing_tier_code character varying(50) NOT NULL,
    credit_limit numeric(19,4) NOT NULL,
    currency character varying(10) NOT NULL,
    current_balance numeric(19,4) NOT NULL,
    effective_from date,
    effective_to date,
    invoice_due_days integer NOT NULL,
    low_balance_threshold numeric(19,4) NOT NULL,
    status character varying(50) NOT NULL,
    unit_price numeric(19,4) NOT NULL,
    company_id character varying(255) NOT NULL,
    CONSTRAINT company_billing_profiles_billing_mode_check CHECK (((billing_mode)::text = ANY ((ARRAY['PREPAID'::character varying, 'POSTPAID'::character varying])::text[]))),
    CONSTRAINT company_billing_profiles_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.company_communications (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    billing_email character varying(255),
    email character varying(255) NOT NULL,
    email_notifications boolean,
    notification_settings jsonb,
    payload_fields jsonb,
    phone character varying(255),
    secondary_email character varying(255),
    secondary_phone character varying(255),
    sms_notifications boolean,
    support_email character varying(255),
    webhook_enabled boolean,
    webhook_secret character varying(255),
    webhook_url character varying(255),
    website character varying(255),
    company_id character varying(255) NOT NULL
);

CREATE TABLE public.company_credentials (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    api_key_hash character varying(255),
    api_secret_hash character varying(255),
    dev_api_key_hash character varying(255),
    dev_api_secret_hash character varying(255),
    dev_expires_at timestamp(6) without time zone,
    dev_last_used_at timestamp(6) without time zone,
    dev_rate_limit_per_day integer,
    dev_rate_limit_per_hour integer,
    dev_rate_limit_per_month integer,
    dev_status character varying(255),
    dev_total_failed_requests bigint,
    dev_total_requests bigint,
    dev_total_successful_requests bigint,
    expires_at timestamp(6) without time zone,
    last_used_at timestamp(6) without time zone,
    metadata jsonb,
    prod_api_key_hash character varying(255),
    prod_api_secret_hash character varying(255),
    prod_expires_at timestamp(6) without time zone,
    prod_last_used_at timestamp(6) without time zone,
    prod_rate_limit_per_day integer,
    prod_rate_limit_per_hour integer,
    prod_rate_limit_per_month integer,
    prod_status character varying(255),
    prod_total_failed_requests bigint,
    prod_total_requests bigint,
    prod_total_successful_requests bigint,
    rate_limit_per_day integer,
    rate_limit_per_hour integer,
    rate_limit_per_month integer,
    security_settings jsonb,
    status character varying(255) NOT NULL,
    total_failed_requests bigint,
    total_requests bigint,
    total_successful_requests bigint,
    company_id character varying(255) NOT NULL,
    CONSTRAINT company_credentials_dev_status_check CHECK (((dev_status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT company_credentials_prod_status_check CHECK (((prod_status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT company_credentials_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.company_tbl (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    application_id character varying(255),
    approved_at timestamp(6) without time zone,
    approved_by character varying(255),
    business_type character varying(255),
    company_identifier_code character varying(255) NOT NULL,
    country character varying(2),
    legal_name character varying(255) NOT NULL,
    extra_details jsonb,
    registration_number character varying(255),
    status character varying(50) NOT NULL,
    trading_name character varying(255),
    CONSTRAINT company_tbl_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.event_publication (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone
);

CREATE TABLE public.kyc_draft_documents (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    document_type character varying(30) NOT NULL,
    draft_id character varying(255) NOT NULL,
    file_size bigint NOT NULL,
    mime_type character varying(100),
    original_filename character varying(255),
    storage_key character varying(512) NOT NULL,
    uploaded_at timestamp(6) without time zone NOT NULL
);

CREATE TABLE public.kyc_drafts (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    company_id character varying(255) NOT NULL,
    details jsonb,
    environment character varying(10) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    external_reference character varying(255),
    resume_token_hash character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    status_changed_at timestamp(6) without time zone,
    tier character varying(20) NOT NULL,
    verification_id character varying(255),
    version bigint NOT NULL,
    workflow_id character varying(255),
    workflow_version integer,
    CONSTRAINT kyc_drafts_environment_check CHECK (((environment)::text = ANY ((ARRAY['DEV'::character varying, 'PROD'::character varying])::text[]))),
    CONSTRAINT kyc_drafts_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'SUBMITTING'::character varying, 'SUBMITTED'::character varying])::text[])))
);

CREATE TABLE public.kyc_handoffs (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    claimed_at timestamp(6) without time zone,
    claimed_user_agent character varying(255),
    code_hash character varying(64) NOT NULL,
    draft_id character varying(255) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT kyc_handoffs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CLAIMED'::character varying, 'REVOKED'::character varying])::text[])))
);

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    message text NOT NULL,
    read_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    company_id character varying(255),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY ((ARRAY['UNREAD'::character varying, 'READ'::character varying])::text[]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['INFO'::character varying, 'SUCCESS'::character varying, 'WARNING'::character varying, 'ERROR'::character varying])::text[])))
);

ALTER TABLE public.notifications ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.permissions (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    action character varying(50) NOT NULL,
    description text,
    name character varying(100) NOT NULL,
    resource character varying(50) NOT NULL
);

CREATE TABLE public.refresh_tokens (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    expires_at timestamp(6) without time zone NOT NULL,
    ip_address character varying(45),
    revoked boolean,
    session_id character varying(255),
    token_hash character varying(255) NOT NULL,
    user_agent text,
    user_id character varying(255) NOT NULL
);

CREATE TABLE public.role_permissions (
    role_id character varying(255) NOT NULL,
    permission_id character varying(255) NOT NULL
);

CREATE TABLE public.roles (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    description text,
    name character varying(50) NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT roles_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.screening_tenant (
    company_id character varying(255) NOT NULL,
    tenant_id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.service_pings (
    id bigint NOT NULL,
    checked_at timestamp(6) without time zone NOT NULL,
    error character varying(300),
    latency_ms bigint NOT NULL,
    service character varying(40) NOT NULL,
    status_code integer,
    up boolean NOT NULL
);

ALTER TABLE public.service_pings ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.service_pings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.trusted_devices (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    device_label character varying(255),
    device_token_hash character varying(255) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    ip_address character varying(45),
    last_used_at timestamp(6) without time zone,
    revoked boolean,
    user_agent text,
    user_id character varying(255) NOT NULL
);

CREATE TABLE public.user_activity_logs (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    activity_type character varying(50) NOT NULL,
    completed_at timestamp(6) without time zone,
    duration_ms bigint,
    http_method character varying(16),
    ip_address character varying(45),
    path character varying(500) NOT NULL,
    query_string text,
    resource_id character varying(255),
    resource_type character varying(100),
    started_at timestamp(6) without time zone NOT NULL,
    status_code integer,
    company_id character varying(255),
    session_id_fk character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);

CREATE TABLE public.user_roles (
    user_id character varying(255) NOT NULL,
    role_id character varying(255) NOT NULL
);

CREATE TABLE public.user_sessions (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    ip_address character varying(45),
    last_activity_at timestamp(6) without time zone,
    login_at timestamp(6) without time zone NOT NULL,
    logout_at timestamp(6) without time zone,
    session_id character varying(255) NOT NULL,
    status character varying(50) NOT NULL,
    user_agent text,
    company_id character varying(255),
    refresh_token_id character varying(255),
    user_id character varying(255) NOT NULL
);

CREATE TABLE public.users (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    email character varying(255) NOT NULL,
    email_verified boolean,
    failed_login_attempts integer,
    first_name character varying(100),
    google_authenticator_key character varying(255),
    is_2fa_enabled boolean,
    is_active boolean,
    last_login_at timestamp(6) without time zone,
    last_name character varying(100),
    locked_until timestamp(6) without time zone,
    must_change_password boolean,
    password_changed_at timestamp(6) without time zone,
    password_hash character varying(255) NOT NULL,
    phone character varying(20),
    status character varying(255) NOT NULL,
    username character varying(100) NOT NULL,
    company_id character varying(255),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.verification_documents (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    document_type character varying(50) NOT NULL,
    extracted_data jsonb,
    file_path character varying(255) NOT NULL,
    file_size bigint,
    mime_type character varying(100),
    original_filename character varying(255),
    verification_id character varying(255) NOT NULL,
    storage_key character varying(512)
);

CREATE TABLE public.verification_requests (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    billed_at timestamp(6) without time zone,
    billing_amount numeric(19,4),
    completed_at timestamp(6) without time zone,
    customer_data jsonb,
    environment character varying(10) NOT NULL,
    error_details text,
    external_reference character varying(255),
    failure_reason character varying(100),
    metadata jsonb,
    overall_decision character varying(50),
    risk_score double precision,
    status character varying(255) NOT NULL,
    tier character varying(20) NOT NULL,
    verification_type character varying(20),
    webhook_url character varying(255),
    company_id character varying(255) NOT NULL,
    workflow_id character varying(255),
    workflow_version integer,
    workflow_path jsonb,
    review_assigned_at timestamp(6) without time zone,
    review_assigned_to character varying(255),
    review_notes text,
    review_reason character varying(500),
    review_requested_at timestamp(6) without time zone,
    reviewed_at timestamp(6) without time zone,
    reviewed_by character varying(255),
    processing_started_at timestamp(6) without time zone,
    CONSTRAINT verification_requests_environment_check CHECK (((environment)::text = ANY ((ARRAY['DEV'::character varying, 'PROD'::character varying])::text[]))),
    CONSTRAINT verification_requests_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.verification_steps (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    confidence_score double precision,
    error_message character varying(255),
    executed_at timestamp(6) without time zone,
    result jsonb,
    status character varying(255) NOT NULL,
    step_name character varying(50) NOT NULL,
    verification_id character varying(255) NOT NULL,
    duration_ms bigint,
    started_at timestamp(6) without time zone,
    CONSTRAINT verification_steps_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'PROCESSING'::character varying, 'DRAFT'::character varying, 'INFO_REQUESTED'::character varying, 'REJECTED'::character varying, 'ON_HOLD'::character varying, 'ARCHIVED'::character varying, 'UNKNOWN'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.wallet_transactions (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    amount numeric(19,4) NOT NULL,
    balance_after numeric(19,4) NOT NULL,
    balance_before numeric(19,4) NOT NULL,
    currency character varying(10) NOT NULL,
    description text,
    reference character varying(255),
    type character varying(20) NOT NULL,
    verification_request_id character varying(255),
    billing_profile_id character varying(255) NOT NULL,
    company_id character varying(255) NOT NULL,
    CONSTRAINT wallet_transactions_type_check CHECK (((type)::text = ANY ((ARRAY['TOPUP'::character varying, 'DEBIT'::character varying, 'ADJUSTMENT'::character varying, 'REFUND'::character varying])::text[])))
);

CREATE TABLE public.webhook_deliveries (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_at timestamp(6) without time zone,
    updated_by character varying(255),
    delivered_at timestamp(6) without time zone,
    error_message text,
    event_type character varying(100) NOT NULL,
    http_status_code integer,
    next_retry_at timestamp(6) without time zone,
    payload text NOT NULL,
    response_body text,
    retry_count integer NOT NULL,
    status character varying(50) NOT NULL,
    verification_id character varying(255),
    webhook_url character varying(500) NOT NULL,
    company_id character varying(255) NOT NULL,
    CONSTRAINT webhook_deliveries_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'RETRYING'::character varying])::text[])))
);

CREATE TABLE public.workflow_definition (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    company_id character varying(255),
    workflow_key character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    subject_type character varying(20) NOT NULL,
    draft_graph jsonb NOT NULL,
    draft_updated_at timestamp(6) without time zone,
    latest_version integer DEFAULT 0 NOT NULL,
    live_version integer,
    archived boolean DEFAULT false NOT NULL,
    CONSTRAINT workflow_definition_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'BUSINESS'::character varying])::text[])))
);

CREATE TABLE public.workflow_service (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(50) NOT NULL,
    status character varying(30) NOT NULL,
    subject_types jsonb DEFAULT '[]'::jsonb NOT NULL,
    countries jsonb DEFAULT '[]'::jsonb NOT NULL,
    requires jsonb DEFAULT '[]'::jsonb NOT NULL,
    required_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    captures jsonb DEFAULT '[]'::jsonb NOT NULL,
    outputs jsonb DEFAULT '[]'::jsonb NOT NULL,
    ui jsonb DEFAULT '{}'::jsonb NOT NULL,
    settings_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT workflow_service_category_check CHECK (((category)::text = ANY ((ARRAY['VERIFICATION'::character varying, 'DOCUMENT'::character varying, 'REGISTRY'::character varying, 'SCREENING'::character varying, 'RISK'::character varying])::text[]))),
    CONSTRAINT workflow_service_status_check CHECK (((status)::text = ANY ((ARRAY['IN_DEVELOPMENT'::character varying, 'AVAILABLE'::character varying, 'DEPRECATED'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT workflow_service_subject_types_check CHECK ((subject_types <@ '["INDIVIDUAL", "BUSINESS"]'::jsonb))
);

CREATE TABLE public.workflow_version (
    id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    definition_id character varying(255) NOT NULL,
    version_number integer NOT NULL,
    graph jsonb NOT NULL,
    services jsonb NOT NULL,
    price numeric(19,4),
    currency character varying(3),
    notes text,
    published_by character varying(255),
    published_at timestamp(6) without time zone NOT NULL,
    promoted_at timestamp(6) without time zone
);

ALTER TABLE ONLY public.backup_codes
    ADD CONSTRAINT backup_codes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT billing_charges_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.billing_invoice_lines
    ADD CONSTRAINT billing_invoice_lines_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT billing_invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_application_approvals
    ADD CONSTRAINT company_application_approvals_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_application
    ADD CONSTRAINT company_application_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_billing_profiles
    ADD CONSTRAINT company_billing_profiles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_communications
    ADD CONSTRAINT company_communications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT company_credentials_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.company_tbl
    ADD CONSTRAINT company_tbl_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT idx_billing_charge_verification UNIQUE (verification_request_id);

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT idx_billing_invoice_number UNIQUE (invoice_number);

ALTER TABLE ONLY public.company_billing_profiles
    ADD CONSTRAINT idx_company_billing_company UNIQUE (company_id);

ALTER TABLE ONLY public.kyc_draft_documents
    ADD CONSTRAINT kyc_draft_documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.kyc_drafts
    ADD CONSTRAINT kyc_drafts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.kyc_handoffs
    ADD CONSTRAINT kyc_handoffs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.screening_tenant
    ADD CONSTRAINT screening_tenant_pkey PRIMARY KEY (company_id);

ALTER TABLE ONLY public.screening_tenant
    ADD CONSTRAINT screening_tenant_tenant_id_key UNIQUE (tenant_id);

ALTER TABLE ONLY public.service_pings
    ADD CONSTRAINT service_pings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trusted_devices
    ADD CONSTRAINT trusted_devices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trusted_devices
    ADD CONSTRAINT uk5ojdakn9mgnfkatk71ciuey07 UNIQUE (device_token_hash);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_email_company UNIQUE (email, company_id);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT ukacbk4h740a15vs1rffxwml4un UNIQUE (dev_api_key_hash);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT ukbjoac5vd2jt3pnrfrdeb49014 UNIQUE (session_id);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT uke54s62riohyuyqaetohmomu6e UNIQUE (prod_api_key_hash);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT ukho9oe6iwvuuv4pm8fvnbhkrm4 UNIQUE (refresh_token_id);

ALTER TABLE ONLY public.company_application
    ADD CONSTRAINT ukhqfcs1x11x99bmy3mgp5x5dka UNIQUE (application_number);

ALTER TABLE ONLY public.backup_codes
    ADD CONSTRAINT uki7awbbx0242cms20rjixh7bkm UNIQUE (code_hash);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT ukkq5wblsy3jsx00xaokxss2cxe UNIQUE (api_key_hash);

ALTER TABLE ONLY public.kyc_handoffs
    ADD CONSTRAINT ukkq6hfm0txtxjnbmo1mlkev7g6 UNIQUE (code_hash);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT ukn4cc1u7ew0c496635m2fhxd7h UNIQUE (company_id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT ukofx66keruapi6vyqpv6f2or37 UNIQUE (name);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT ukpnvtwliis6p05pn6i3ndjrqt2 UNIQUE (name);

ALTER TABLE ONLY public.company_tbl
    ADD CONSTRAINT ukq5oafj8bc060n64u7lkih6vae UNIQUE (company_identifier_code);

ALTER TABLE ONLY public.company_communications
    ADD CONSTRAINT ukr3e9ya6k9jps5ugo6jj3hxnsf UNIQUE (company_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT ukr43af9ap4edm43mmtq01oddj6 UNIQUE (username);

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT uq_workflow_definition_company_key UNIQUE (company_id, workflow_key);

ALTER TABLE ONLY public.workflow_version
    ADD CONSTRAINT uq_workflow_version_number UNIQUE (definition_id, version_number);

ALTER TABLE ONLY public.user_activity_logs
    ADD CONSTRAINT user_activity_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.verification_documents
    ADD CONSTRAINT verification_documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.verification_requests
    ADD CONSTRAINT verification_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.verification_steps
    ADD CONSTRAINT verification_steps_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.webhook_deliveries
    ADD CONSTRAINT webhook_deliveries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT workflow_definition_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.workflow_service
    ADD CONSTRAINT workflow_service_code_key UNIQUE (code);

ALTER TABLE ONLY public.workflow_service
    ADD CONSTRAINT workflow_service_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.workflow_version
    ADD CONSTRAINT workflow_version_pkey PRIMARY KEY (id);

CREATE INDEX event_publication_by_completion_date_idx ON public.event_publication USING btree (completion_date);

CREATE INDEX event_publication_serialized_event_hash_idx ON public.event_publication USING hash (serialized_event);

CREATE INDEX idx_backup_codes_code_hash ON public.backup_codes USING btree (code_hash);

CREATE INDEX idx_backup_codes_user_id ON public.backup_codes USING btree (user_id);

CREATE INDEX idx_billing_charge_company_status ON public.billing_charges USING btree (company_id, status);

CREATE INDEX idx_billing_invoice_company ON public.billing_invoices USING btree (company_id);

CREATE INDEX idx_comm_email ON public.company_communications USING btree (email);

CREATE INDEX idx_comm_phone ON public.company_communications USING btree (phone);

CREATE INDEX idx_company_id ON public.users USING btree (company_id);

CREATE INDEX idx_email ON public.users USING btree (email);

CREATE INDEX idx_expires_at ON public.refresh_tokens USING btree (expires_at);

CREATE INDEX idx_permission_name ON public.permissions USING btree (name);

CREATE INDEX idx_resource_action ON public.permissions USING btree (resource, action);

CREATE INDEX idx_role_name ON public.roles USING btree (name);

CREATE INDEX idx_service_pings_service_checked ON public.service_pings USING btree (service, checked_at);

CREATE INDEX idx_status ON public.users USING btree (status);

CREATE INDEX idx_token_hash ON public.refresh_tokens USING btree (token_hash);

CREATE INDEX idx_trusted_devices_expires_at ON public.trusted_devices USING btree (expires_at);

CREATE INDEX idx_trusted_devices_token_hash ON public.trusted_devices USING btree (device_token_hash);

CREATE INDEX idx_trusted_devices_user_id ON public.trusted_devices USING btree (user_id);

CREATE INDEX idx_user_activity_logs_company_id ON public.user_activity_logs USING btree (company_id);

CREATE INDEX idx_user_activity_logs_http_method ON public.user_activity_logs USING btree (http_method);

CREATE INDEX idx_user_activity_logs_path ON public.user_activity_logs USING btree (path);

CREATE INDEX idx_user_activity_logs_session_id ON public.user_activity_logs USING btree (session_id_fk);

CREATE INDEX idx_user_activity_logs_started_at ON public.user_activity_logs USING btree (started_at);

CREATE INDEX idx_user_activity_logs_user_id ON public.user_activity_logs USING btree (user_id);

CREATE INDEX idx_user_id ON public.refresh_tokens USING btree (user_id);

CREATE INDEX idx_user_sessions_company_id ON public.user_sessions USING btree (company_id);

CREATE INDEX idx_user_sessions_login_at ON public.user_sessions USING btree (login_at);

CREATE INDEX idx_user_sessions_status ON public.user_sessions USING btree (status);

CREATE INDEX idx_user_sessions_user_id ON public.user_sessions USING btree (user_id);

CREATE INDEX idx_username ON public.users USING btree (username);

CREATE INDEX idx_verification_requests_review_queue ON public.verification_requests USING btree (status, review_assigned_to);

CREATE INDEX idx_wallet_transaction_company ON public.wallet_transactions USING btree (company_id);

CREATE INDEX idx_webhook_company ON public.webhook_deliveries USING btree (company_id);

CREATE INDEX idx_webhook_next_retry ON public.webhook_deliveries USING btree (next_retry_at);

CREATE INDEX idx_webhook_status ON public.webhook_deliveries USING btree (status);

CREATE UNIQUE INDEX uq_workflow_definition_template_key ON public.workflow_definition USING btree (workflow_key) WHERE (company_id IS NULL);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT fk1fi51hb5dp9e60yq5mwdf2pfe FOREIGN KEY (invoice_id) REFERENCES public.billing_invoices(id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT fk1snbhrx3qba3mc71hrwr3ik58 FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT fk1uf4cy3ir9fuav36d4hwvsoo1 FOREIGN KEY (billing_profile_id) REFERENCES public.company_billing_profiles(id);

ALTER TABLE ONLY public.verification_requests
    ADD CONSTRAINT fk2q6aryh2xcxhkpbusv7i7s2b5 FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT fk4r6992jfepky4aa2423yppdlo FOREIGN KEY (verification_request_id) REFERENCES public.verification_requests(id);

ALTER TABLE ONLY public.backup_codes
    ADD CONSTRAINT fk4uqupj7j6eldpff5ycw8vnpm6 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.trusted_devices
    ADD CONSTRAINT fk4usfnuew32oxr41qn5xlp34hk FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT fk75oxxkedcrgcpfd60dkm6ek62 FOREIGN KEY (refresh_token_id) REFERENCES public.refresh_tokens(id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk7jpfdkdek3j4q5sdn1edcbrra FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.company_billing_profiles
    ADD CONSTRAINT fk8iw4ph8tfb30wgsima29uahl FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT fk8klxsgb8dcjjklmqebqp1twd5 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.webhook_deliveries
    ADD CONSTRAINT fk8llctxol7yt4ano9cecr6qmk4 FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT fkab4soemmmptyhmh69t6q1an3f FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.company_communications
    ADD CONSTRAINT fkapbv24p2337jdbt807dpcq65k FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.user_activity_logs
    ADD CONSTRAINT fkbyi64n0h5yytuemgpsmo7fumq FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.user_activity_logs
    ADD CONSTRAINT fkci4pq8q71xm6breqdpko42wrd FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.billing_invoice_lines
    ADD CONSTRAINT fkdjqkeuamitemfi3agblbbwt49 FOREIGN KEY (invoice_id) REFERENCES public.billing_invoices(id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);

ALTER TABLE ONLY public.verification_steps
    ADD CONSTRAINT fkfu1qpl0e80pifxwvdff2ph5qn FOREIGN KEY (verification_id) REFERENCES public.verification_requests(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.company_credentials
    ADD CONSTRAINT fkhnt7k3vgrjl8gjf5txwpd1p0 FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fki62xyi3742g3s8v0qpttepolu FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.user_activity_logs
    ADD CONSTRAINT fkj20aupab4xdba8qmee6acpa9a FOREIGN KEY (session_id_fk) REFERENCES public.user_sessions(id);

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT fkja0larne4cf56cql0ct9li0wk FOREIGN KEY (billing_profile_id) REFERENCES public.company_billing_profiles(id);

ALTER TABLE ONLY public.verification_documents
    ADD CONSTRAINT fklfdntfhalyid1ygikke6kbac3 FOREIGN KEY (verification_id) REFERENCES public.verification_requests(id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.billing_invoices
    ADD CONSTRAINT fkoljhoy8oqs9jhxbr7ol474ygl FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT fkovscpco76ln7n3jhbkceoyqwy FOREIGN KEY (billing_profile_id) REFERENCES public.company_billing_profiles(id);

ALTER TABLE ONLY public.billing_charges
    ADD CONSTRAINT fkssf6f8m7bt3gh8q28b7oqgpk7 FOREIGN KEY (company_id) REFERENCES public.company_tbl(id);

ALTER TABLE ONLY public.workflow_version
    ADD CONSTRAINT workflow_version_definition_id_fkey FOREIGN KEY (definition_id) REFERENCES public.workflow_definition(id);

-- Reference data

INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('5371e9fe-84f3-4dfb-a574-d33c4b4ca677', '2026-11-06 04:18:00.857816', 'b53cfbcd-a8c1-49bc-a181-4c26950778fc', '2026-11-06 04:18:00.857854', NULL, 'create', 'Create new company', 'company:create', 'company');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('d9ff0e13-2de6-451b-9d2f-34f4ea2ec824', '2026-11-06 04:18:00.908845', 'a6269104-b893-4f9a-889d-4e0ee81d6f45', '2026-11-06 04:18:00.908873', NULL, 'read', 'View company details', 'company:read', 'company');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('45eaf651-40d7-48be-a61e-f6bbaf5e4fac', '2026-11-06 04:18:00.915677', 'bcec9752-de5d-421a-b1c0-293885f2f0ea', '2026-11-06 04:18:00.915723', NULL, 'update', 'Update company information', 'company:update', 'company');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('359a045c-dec3-4c2b-9a8d-a253a083da5d', '2026-11-06 04:18:00.924129', '10520ab2-da26-4e1a-a806-afb0cc36170f', '2026-11-06 04:18:00.924149', NULL, 'delete', 'Delete company', 'company:delete', 'company');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('1c3c155c-4fc7-4a9b-9936-008e1b48ae6a', '2026-11-06 04:18:00.9311', 'd5568d6a-ec5d-46f0-8117-63501ca5ee15', '2026-11-06 04:18:00.931303', NULL, 'create', 'Create application', 'application:create', 'application');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('42182286-866a-4a42-8abf-192943a6b755', '2026-11-06 04:18:00.939573', 'aa8e0a47-b97e-41b6-bd28-50e007563630', '2026-11-06 04:18:00.939592', NULL, 'read', 'View applications', 'application:read', 'application');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('bc39d9c1-7b1c-4db7-a37d-44670934f537', '2026-11-06 04:18:00.950801', '0567be2c-0486-4768-b261-021272f2c1b1', '2026-11-06 04:18:00.95082', NULL, 'update', 'Update application', 'application:update', 'application');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('eb71725a-8681-4a1a-bd25-31aaba49d936', '2026-11-06 04:18:00.961192', 'a8628c57-c0bb-477b-9efd-64ba945d3914', '2026-11-06 04:18:00.961216', NULL, 'approve', 'Approve application', 'application:approve', 'application');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('74b45d60-1e22-44ea-aa47-b74b30bfbcc7', '2026-11-06 04:18:00.972459', '3142e7fa-7a9e-43df-afd8-a5bbc8826121', '2026-11-06 04:18:00.972479', NULL, 'reject', 'Reject application', 'application:reject', 'application');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('5efd3be9-9bbb-4f62-b8e8-07e8e051489a', '2026-11-06 04:18:00.978729', '45993379-858f-460a-a4f3-5e673f6d2c3e', '2026-11-06 04:18:00.978743', NULL, 'create', 'Create user', 'user:create', 'user');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('90a17987-5715-4e60-8439-04dcc835afa4', '2026-11-06 04:18:00.983716', 'c0efd980-c040-45ac-a0a9-7d9981499840', '2026-11-06 04:18:00.983728', NULL, 'read', 'View users', 'user:read', 'user');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('1dede0a3-f0e9-4bd6-b42c-968fe2063c21', '2026-11-06 04:18:00.989096', '7a24a510-2042-4a9b-aab8-26f37cead17e', '2026-11-06 04:18:00.98911', NULL, 'update', 'Update user', 'user:update', 'user');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('ebfd70b4-aaf8-4ad9-b23f-bbb64bfa6688', '2026-11-06 04:18:00.995002', 'f0e8122d-ddb0-4505-b424-7326cfe3fe99', '2026-11-06 04:18:00.995013', NULL, 'delete', 'Delete user', 'user:delete', 'user');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('24a1e59d-96fa-4070-b12c-333d7e1e1845', '2026-11-06 04:18:01.000552', '32c4458a-9ec4-4038-add7-74f79b7398ce', '2026-11-06 04:18:01.00057', NULL, 'create', 'Create role', 'role:create', 'role');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('c52f355b-b037-4370-8479-337fc983ee88', '2026-11-06 04:18:01.005875', '067428e1-9a35-4ca6-ab41-f0500bcaa53b', '2026-11-06 04:18:01.005888', NULL, 'read', 'View roles', 'role:read', 'role');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('cad4b353-a30c-4ff1-a06a-a6d07838ebf6', '2026-11-06 04:18:01.015417', '9d124988-5f35-4036-b3ce-5232a3ec0c10', '2026-11-06 04:18:01.015449', NULL, 'update', 'Update role', 'role:update', 'role');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('055a4b91-84de-4a96-abae-8e9c25cbfe29', '2026-11-06 04:18:01.020795', 'f8fe538d-b987-41cf-b6d1-5cd740e5c62e', '2026-11-06 04:18:01.020806', NULL, 'delete', 'Delete role', 'role:delete', 'role');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('91ab477d-6a2f-4919-bd0c-ec6d59e804d3', '2026-11-06 04:18:01.024276', 'd5456570-1ca0-4548-917e-8118ff46a557', '2026-11-06 04:18:01.024286', NULL, 'create', 'Create permission', 'permission:create', 'permission');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('d787283e-5335-4801-9928-a6b52ef22416', '2026-11-06 04:18:01.028021', 'f74a78f3-6cb7-4617-ad80-b4daa06f8e05', '2026-11-06 04:18:01.028031', NULL, 'read', 'View permissions', 'permission:read', 'permission');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('c6d98280-90d0-4ec6-b4ca-017b6bcdebaa', '2026-11-06 04:18:01.035643', 'a07ddc0c-1494-4712-8306-2589106a9102', '2026-11-06 04:18:01.035655', NULL, 'delete', 'Delete permission', 'permission:delete', 'permission');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('bcb948e2-5541-4f92-9cf7-ad2bf63017de', '2026-11-06 04:18:01.039871', '3617ac12-0cde-47ae-9735-041ca920e967', '2026-11-06 04:18:01.039881', NULL, 'verify', 'Perform KYC verification', 'kyc:verify', 'kyc');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('435e407f-8c70-4316-8412-e0f82cf8a796', '2026-11-06 04:18:01.043286', '05de065f-4e1d-46f8-a2cc-63b0c45cc1f8', '2026-11-06 04:18:01.043295', NULL, 'read', 'View KYC verifications', 'kyc:read', 'kyc');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('4759f1ee-1bb1-4b68-a1d7-dfcaa67462b9', '2026-11-06 04:18:01.058774', 'baae8bae-7646-4044-bc56-962d8219be1d', '2026-11-06 04:18:01.058799', NULL, 'create', 'Generate API keys', 'apikey:create', 'apikey');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('e06b6180-baf9-41f2-9380-803f96002ad5', '2026-11-06 04:18:01.064867', '171ecdb4-1497-43a4-aa6e-4885d867c970', '2026-11-06 04:18:01.064888', NULL, 'read', 'View API keys', 'apikey:read', 'apikey');
INSERT INTO public.permissions (id, created_at, created_by, updated_at, updated_by, action, description, name, resource) VALUES ('bf5cbbf5-52d6-447b-aa19-45fed4746298', '2026-11-06 04:18:01.073191', '8c7dcbba-5886-4321-b3ad-82165fba56ea', '2026-11-06 04:18:01.073213', NULL, 'revoke', 'Revoke API keys', 'apikey:revoke', 'apikey');

INSERT INTO public.roles (id, created_at, created_by, updated_at, updated_by, description, name, status) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '2026-11-06 04:18:01.128769', '46435918-8eb8-47e7-a0f6-01bba94d5130', '2026-11-06 04:18:01.128793', NULL, 'Super Administrator with full system access', 'SUPER_ADMIN', 'ACTIVE');
INSERT INTO public.roles (id, created_at, created_by, updated_at, updated_by, description, name, status) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '2026-11-06 04:18:01.165775', '14a352da-fbaf-49ec-9d8b-edfe50b58a36', '2026-11-06 04:18:01.165796', NULL, 'Administrator for company and application management', 'ADMIN', 'ACTIVE');
INSERT INTO public.roles (id, created_at, created_by, updated_at, updated_by, description, name, status) VALUES ('bf1df025-e683-4f84-81ed-fb6612b199c4', '2026-11-06 04:18:01.182999', '7cc01440-dc4f-45eb-b50f-7b9994fe2952', '2026-11-06 04:18:01.183018', NULL, 'Final approval authority for applications', 'CHECKER', 'ACTIVE');
INSERT INTO public.roles (id, created_at, created_by, updated_at, updated_by, description, name, status) VALUES ('bcbfdd07-cae3-407c-a1d8-d896370fedcd', '2026-11-06 04:18:01.19458', '9c6be800-05ff-4800-af60-58c26e5b96e4', '2026-11-06 04:18:01.194598', NULL, 'Initial review authority for applications', 'MAKER', 'ACTIVE');
INSERT INTO public.roles (id, created_at, created_by, updated_at, updated_by, description, name, status) VALUES ('3d38119d-9252-4f04-86be-6844b1221ec3', '2026-11-06 04:18:01.203942', '5801be1d-10f1-47b7-954a-92f26c8ab75c', '2026-11-06 04:18:01.203957', NULL, 'Read-only access to system', 'VIEWER', 'ACTIVE');

INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'eb71725a-8681-4a1a-bd25-31aaba49d936');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '359a045c-dec3-4c2b-9a8d-a253a083da5d');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '5efd3be9-9bbb-4f62-b8e8-07e8e051489a');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'e06b6180-baf9-41f2-9380-803f96002ad5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '055a4b91-84de-4a96-abae-8e9c25cbfe29');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '42182286-866a-4a42-8abf-192943a6b755');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'bf5cbbf5-52d6-447b-aa19-45fed4746298');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'ebfd70b4-aaf8-4ad9-b23f-bbb64bfa6688');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '91ab477d-6a2f-4919-bd0c-ec6d59e804d3');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '24a1e59d-96fa-4070-b12c-333d7e1e1845');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'd787283e-5335-4801-9928-a6b52ef22416');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '1c3c155c-4fc7-4a9b-9936-008e1b48ae6a');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '435e407f-8c70-4316-8412-e0f82cf8a796');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'c6d98280-90d0-4ec6-b4ca-017b6bcdebaa');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '90a17987-5715-4e60-8439-04dcc835afa4');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '74b45d60-1e22-44ea-aa47-b74b30bfbcc7');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'd9ff0e13-2de6-451b-9d2f-34f4ea2ec824');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'c52f355b-b037-4370-8479-337fc983ee88');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'bc39d9c1-7b1c-4db7-a37d-44670934f537');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '5371e9fe-84f3-4dfb-a574-d33c4b4ca677');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'bcb948e2-5541-4f92-9cf7-ad2bf63017de');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '4759f1ee-1bb1-4b68-a1d7-dfcaa67462b9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '45eaf651-40d7-48be-a61e-f6bbaf5e4fac');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', '1dede0a3-f0e9-4bd6-b42c-968fe2063c21');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('ee793eaa-ce0e-451a-9312-162fc8df6d8d', 'cad4b353-a30c-4ff1-a06a-a6d07838ebf6');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', 'e06b6180-baf9-41f2-9380-803f96002ad5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', 'bc39d9c1-7b1c-4db7-a37d-44670934f537');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '435e407f-8c70-4316-8412-e0f82cf8a796');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '5371e9fe-84f3-4dfb-a574-d33c4b4ca677');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '42182286-866a-4a42-8abf-192943a6b755');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', 'bf5cbbf5-52d6-447b-aa19-45fed4746298');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '4759f1ee-1bb1-4b68-a1d7-dfcaa67462b9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', '45eaf651-40d7-48be-a61e-f6bbaf5e4fac');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('02a0043d-2d51-4467-8111-81b405ba8350', 'd9ff0e13-2de6-451b-9d2f-34f4ea2ec824');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bf1df025-e683-4f84-81ed-fb6612b199c4', '42182286-866a-4a42-8abf-192943a6b755');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bf1df025-e683-4f84-81ed-fb6612b199c4', 'eb71725a-8681-4a1a-bd25-31aaba49d936');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bf1df025-e683-4f84-81ed-fb6612b199c4', '74b45d60-1e22-44ea-aa47-b74b30bfbcc7');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bf1df025-e683-4f84-81ed-fb6612b199c4', 'd9ff0e13-2de6-451b-9d2f-34f4ea2ec824');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bcbfdd07-cae3-407c-a1d8-d896370fedcd', 'bc39d9c1-7b1c-4db7-a37d-44670934f537');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bcbfdd07-cae3-407c-a1d8-d896370fedcd', '42182286-866a-4a42-8abf-192943a6b755');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('bcbfdd07-cae3-407c-a1d8-d896370fedcd', 'd9ff0e13-2de6-451b-9d2f-34f4ea2ec824');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3d38119d-9252-4f04-86be-6844b1221ec3', '435e407f-8c70-4316-8412-e0f82cf8a796');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3d38119d-9252-4f04-86be-6844b1221ec3', '42182286-866a-4a42-8abf-192943a6b755');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3d38119d-9252-4f04-86be-6844b1221ec3', 'd9ff0e13-2de6-451b-9d2f-34f4ea2ec824');

INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('2fd2400d-966a-4a2b-8b2c-b5a55acecdda', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'DOCUMENT_CHECK', 'ID document check', 'Capture the ID document and read it (OCR).', 'VERIFICATION', 'AVAILABLE', '["INDIVIDUAL"]', '[]', '[]', '[]', '["id_front", "id_back"]', '[{"key": "document.type", "type": "string"}, {"key": "document.country", "type": "string"}, {"key": "document.ocr_confidence", "type": "number"}]', '{"icon": "id-card", "screen": "document_capture"}', '{"type": "object", "properties": {"acceptedDocuments": {"type": "array", "items": {"enum": ["national_id", "passport", "drivers_license"]}}}}', 10);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('7740c171-ab12-47e5-a420-c2a1cd46c89b', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'DATA_CROSSCHECK', 'Data cross-check', 'Compare what the applicant entered with what the document says.', 'VERIFICATION', 'AVAILABLE', '["INDIVIDUAL"]', '[]', '["DOCUMENT_CHECK"]', '["firstName", "lastName", "dateOfBirth", "idNumber"]', '[]', '[{"key": "crosscheck.name_match", "type": "boolean"}, {"key": "crosscheck.id_number_match", "type": "boolean"}, {"key": "crosscheck.dob_match", "type": "boolean"}]', '{"icon": "list-check"}', '{}', 20);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('39a5fda5-81cb-45ea-af62-930c44a66d9a', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'LIVENESS', 'Selfie and liveness', 'Active liveness: the applicant follows head-turn prompts; frames are checked server-side.', 'VERIFICATION', 'AVAILABLE', '["INDIVIDUAL"]', '[]', '[]', '[]', '["selfie", "liveness_frames"]', '[{"key": "liveness.passed", "type": "boolean"}, {"key": "liveness.score", "type": "number"}]', '{"icon": "face-scan", "screen": "selfie_liveness"}', '{"type": "object", "properties": {"mode": {"enum": ["active"]}}}', 30);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('2f083cc6-c336-4ead-8485-ef7e6d03d5e8', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'FACE_MATCH', 'Face match', 'Compare the selfie with the photo on the ID document.', 'VERIFICATION', 'AVAILABLE', '["INDIVIDUAL"]', '[]', '["DOCUMENT_CHECK", "LIVENESS"]', '[]', '[]', '[{"key": "face.matched", "type": "boolean"}, {"key": "face.match_score", "type": "number"}]', '{"icon": "faces"}', '{"type": "object", "properties": {"threshold": {"type": "number", "maximum": 1, "minimum": 0}}}', 40);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('0f0c8835-b29f-4d21-8bb3-8bd65f7722a2', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'AGE_CHECK', 'Age check', 'Check the applicant is old enough, from the date of birth on the document.', 'VERIFICATION', 'IN_DEVELOPMENT', '["INDIVIDUAL"]', '[]', '["DOCUMENT_CHECK"]', '[]', '[]', '[{"key": "applicant.age", "type": "number"}, {"key": "age.passed", "type": "boolean"}]', '{"icon": "calendar"}', '{"type": "object", "required": ["minimumAge"], "properties": {"minimumAge": {"type": "integer", "minimum": 0}}}', 50);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('1d9edfb7-3491-4098-b783-122c79364f5e', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'IPRS_LOOKUP', 'IPRS registry match', 'Match the applicant against Kenya''s national population register (IPRS).', 'REGISTRY', 'IN_DEVELOPMENT', '["INDIVIDUAL"]', '["KEN"]', '[]', '["idNumber", "firstName", "lastName", "dateOfBirth"]', '[]', '[{"key": "registry.match", "type": "boolean"}, {"key": "registry.available", "type": "boolean"}]', '{"icon": "landmark"}', '{}', 60);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('363493c0-688a-42dc-8ebb-6441971a6f4e', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'PROOF_OF_ADDRESS', 'Proof of address', 'Collect and check a recent utility bill or bank statement.', 'DOCUMENT', 'IN_DEVELOPMENT', '["INDIVIDUAL"]', '[]', '[]', '[]', '["proof_of_address"]', '[{"key": "address.verified", "type": "boolean"}]', '{"icon": "home", "screen": "proof_of_address"}', '{}', 70);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('a5f027aa-3db2-4b3c-9725-c7c3c8b9f1e5', '2026-11-09 13:14:25.179119', '2026-11-09 13:14:25.179119', NULL, NULL, 'BUSINESS_REGISTRY', 'Business registry check', 'Confirm the business against the company registry (e.g. CR12 in Kenya).', 'REGISTRY', 'IN_DEVELOPMENT', '["BUSINESS"]', '[]', '[]', '["registrationNumber", "companyName"]', '[]', '[{"key": "business.registered", "type": "boolean"}, {"key": "business.status", "type": "string"}]', '{"icon": "building"}', '{}', 90);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('dbfce169-f4cc-45f9-aefa-b30b10dbedc0', '2026-11-09 13:14:25.179119', '2026-11-12 17:55:06.255109', NULL, NULL, 'CRA', 'Customer risk assessment', 'Score the customer''s risk from the results so far.', 'RISK', 'AVAILABLE', '["INDIVIDUAL", "BUSINESS"]', '[]', '[]', '[]', '[]', '[{"key": "risk.level", "type": "string", "values": ["LOW", "MEDIUM", "HIGH"]}, {"key": "risk.score", "type": "number"}, {"key": "risk.complete", "type": "boolean"}, {"key": "risk.override_applied", "type": "boolean"}]', '{"icon": "gauge"}', '{}', 100);
INSERT INTO public.workflow_service (id, created_at, updated_at, created_by, updated_by, code, name, description, category, status, subject_types, countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order) VALUES ('76fe318b-1377-41fb-9cd4-67c1d8dd4860', '2026-11-09 13:14:25.179119', '2026-11-12 17:55:06.247793', NULL, NULL, 'SANCTIONS_SCREENING', 'Sanctions and PEP screening', 'Screen against sanctions and politically exposed persons lists.', 'SCREENING', 'AVAILABLE', '["INDIVIDUAL", "BUSINESS"]', '[]', '[]', '["firstName", "lastName", "dateOfBirth", "nationality"]', '[]', '[{"key": "screening.verdict", "type": "string", "values": ["NO_HIT", "POTENTIAL_MATCH", "CONFIRMED_MATCH"]}, {"key": "screening.hits", "type": "number"}, {"key": "screening.top_score", "type": "number"}, {"key": "screening.pep", "type": "boolean"}, {"key": "screening.adverse_media", "type": "boolean"}]', '{"icon": "shield-search"}', '{"type": "object", "properties": {"lists": {"type": "array", "items": {"type": "string"}}}}', 80);
