# Bridge Platform Threat Model

## Document Information

| Field | Value |
|-------|-------|
| **Version** | 1.0 |
| **Last Updated** | March 2026 |
| **Status** | Active |
| **Classification** | Internal |

## Executive Summary

This threat model provides a comprehensive security analysis of the Sage Bionetworks Bridge Platform, a Java SpringBoot application designed to support biomedical research by collecting, storing, and managing health data from study participants. The platform handles sensitive Protected Health Information (PHI) and Personally Identifiable Information (PII), making security a paramount concern.

---

## 1. System Overview

### 1.1 Platform Description

Bridge Server is a RESTful API platform that enables mobile health (mHealth) research studies by:
- Managing participant enrollment and consent
- Collecting health data from mobile applications
- Storing and processing research data securely
- Exporting data to Synapse for analysis
- Supporting multiple concurrent research studies (apps)

### 1.2 Architecture Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL ACTORS                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  Mobile Apps    │    Web Browsers    │    Researchers    │    Admins        │
└────────┬────────┴─────────┬──────────┴─────────┬─────────┴─────────┬────────┘
         │                  │                    │                   │
         ▼                  ▼                    ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          BRIDGE SERVER (SpringBoot)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │    REST     │  │   Auth      │  │   Upload    │  │   Export            │ │
│  │    API      │  │   Service   │  │   Service   │  │   Service           │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  Consent    │  │  Account    │  │   Health    │  │  Participant        │ │
│  │  Service    │  │  Service    │  │   Data Svc  │  │  Service            │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
         │                  │                    │                   │
         ▼                  ▼                    ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  AWS RDS    │  │  DynamoDB   │  │     S3      │  │     Redis           │ │
│  │  (MySQL)    │  │             │  │   Buckets   │  │   (ElastiCache)     │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
         │                                           │
         ▼                                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          EXTERNAL INTEGRATIONS                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  Synapse    │  │  AWS SES    │  │  AWS SNS    │  │     AWS SQS         │ │
│  │  Platform   │  │  (Email)    │  │  (SMS)      │  │   (Message Queue)   │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Key Repositories

| Repository | Description |
|------------|-------------|
| BridgeServer2 | Core REST API server |
| BridgeServer2-infra | Infrastructure as Code |
| Bridge-Exporter | Data export to Synapse |
| Bridge-Exporter-infra | Exporter infrastructure |
| BridgeWorkerPlatform | Async worker processes |
| BridgeWorkerPlatform-infra | Worker infrastructure |

---

## 2. Data Classification

### 2.1 Data Categories

| Category | Description | Sensitivity | Examples |
|----------|-------------|-------------|----------|
| **PHI (Protected Health Information)** | Health data subject to HIPAA | Critical | Health records, survey responses, biometric data |
| **PII (Personally Identifiable Information)** | Data that can identify a person | High | Email, phone, name, address |
| **Authentication Data** | Credentials and tokens | Critical | Passwords, session tokens, API keys |
| **Research Data** | Study metadata and configuration | Medium | Study protocols, schedules, assessments |
| **Consent Data** | Participant consent records | High | Consent signatures, sharing preferences |

### 2.2 Data Flow

```
┌─────────────┐                    ┌─────────────┐                    ┌─────────────┐
│   Mobile    │    HTTPS/TLS       │   Bridge    │    Encrypted       │     S3      │
│    App      │ ─────────────────► │   Server    │ ─────────────────► │   Bucket    │
└─────────────┘                    └─────────────┘                    └─────────────┘
      │                                   │                                  │
      │  Health Data Upload               │                                  │
      │  (CMS Encrypted)                  │  Export to                       │
      │                                   │  Synapse                         │
      │                                   ▼                                  │
      │                            ┌─────────────┐                          │
      │                            │  DynamoDB   │                          │
      │                            │  (Metadata) │                          │
      │                            └─────────────┘                          │
      │                                   │                                  │
      │                                   ▼                                  │
      │                            ┌─────────────┐                          │
      │                            │   Synapse   │◄─────────────────────────┘
      │                            │  Platform   │
      │                            └─────────────┘
      │
      │  Consent/Account
      │  Operations
      ▼
┌─────────────┐
│  AWS RDS    │
│  (MySQL)    │
└─────────────┘
```

---

## 3. Trust Boundaries

### 3.1 Trust Boundary Diagram

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                              UNTRUSTED ZONE                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ Participants │  │  Researchers │  │  Admin Users │  │  Attackers   │       │
│  │ (Mobile App) │  │  (Web/API)   │  │  (Internal)  │  │              │       │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘       │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTPS/TLS (Trust Boundary 1)
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                              DMZ / API GATEWAY                                  │
│                                                                                 │
│  • Load Balancer / WAF                                                         │
│  • Rate Limiting                                                               │
│  • TLS Termination                                                             │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ (Trust Boundary 2)
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION TIER                                      │
│                                                                                 │
│  Bridge Server (SpringBoot)                                                    │
│  • Authentication/Authorization                                                │
│  • Business Logic                                                              │
│  • Input Validation                                                            │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ (Trust Boundary 3)
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                              DATA TIER                                          │
│                                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐                           │
│  │   RDS   │  │DynamoDB │  │   S3    │  │  Redis  │                           │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘                           │
└────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ (Trust Boundary 4)
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                         EXTERNAL SERVICES                                       │
│                                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐                           │
│  │ Synapse │  │ AWS SES │  │ AWS SNS │  │  GBF    │                           │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘                           │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Threat Analysis (STRIDE Model)

### 4.1 Spoofing Identity

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| S-01 | Session Hijacking | Stolen session token allows impersonation | Medium | Critical | High |
| S-02 | Credential Stuffing | Reused passwords from data breaches | High | High | High |
| S-03 | OAuth Token Theft | Compromised OAuth tokens for Synapse auth | Medium | High | Medium |
| S-04 | Basic Auth Interception | HTTP Basic credentials captured in transit | Low | High | Medium |
| S-05 | Account Takeover via Email | Email verification bypass | Low | Critical | Medium |

**Existing Controls:**
- Password hashing using BCRYPT (cost=12) and PBKDF2 (250,000 iterations)
- Session tokens stored in Redis with expiration
- Synapse OAuth integration for federated authentication
- TLS/HTTPS encryption in transit (Strict-Transport-Security header)

### 4.2 Tampering

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| T-01 | Health Data Modification | Unauthorized modification of research data | Low | Critical | Medium |
| T-02 | Consent Record Tampering | Modifying consent signatures | Low | Critical | Medium |
| T-03 | Upload Content Manipulation | Modified uploads during transfer | Low | High | Low |
| T-04 | Configuration Tampering | Unauthorized app/study config changes | Low | High | Medium |
| T-05 | Audit Log Tampering | Removing evidence of malicious activity | Low | Medium | Low |

**Existing Controls:**
- MD5 content verification for uploads
- CMS encryption for upload content
- DynamoDB versioning for record integrity
- Role-based access control for administrative functions

### 4.3 Repudiation

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| R-01 | Consent Denial | Participant claims they never consented | Medium | High | Medium |
| R-02 | Data Submission Denial | Denial of submitted health data | Low | Medium | Low |
| R-03 | Admin Action Denial | Admin denies configuration changes | Medium | Medium | Medium |

**Existing Controls:**
- Consent signatures with timestamps stored in database
- Activity events tracking participant actions
- Request logging with caller information
- StudyActivityEvents for tracking study-related events

### 4.4 Information Disclosure

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| I-01 | PHI Exposure | Unauthorized access to health records | Medium | Critical | High |
| I-02 | PII Leakage | Email/phone exposure in error messages | Medium | High | Medium |
| I-03 | Health Code Exposure | Health code identifier leaked | Medium | High | Medium |
| I-04 | API Key Exposure | Sensitive credentials in logs/responses | Low | Critical | Medium |
| I-05 | Cross-App Data Access | One app accessing another app's data | Low | Critical | Medium |
| I-06 | Participant Enumeration | Discovering valid participant accounts | Medium | Medium | Medium |

**Existing Controls:**
- Health code hashing with configurable algorithms
- Sharing scope controls (NO_SHARING, SPONSORS_AND_PARTNERS, ALL_QUALIFIED_RESEARCHERS)
- Query parameter allowlist for logging (sensitive params excluded)
- Health code export disabled by default
- JSON property filtering on responses

### 4.5 Denial of Service

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| D-01 | API Flood | High volume of API requests | High | High | High |
| D-02 | Large File Upload | Oversized uploads consuming resources | Medium | Medium | Medium |
| D-03 | Account Creation Spam | Mass account registration | Medium | Medium | Medium |
| D-04 | Email/SMS Bomb | Flooding verification channels | Medium | Medium | Medium |
| D-05 | Resource Exhaustion | Memory/CPU exhaustion via complex queries | Medium | High | Medium |

**Existing Controls:**
- Rate limiting for participant creation (3 per 5 minutes in production)
- Channel throttling for email/SMS verification requests
- ByteRateLimiter for file downloads (1MB initial, 10MB max in production)
- Upload size limits (50MB max)
- Zip archive limits (100MB per entry, 100 entries max)
- Redis connection pooling (max 50 connections)

### 4.6 Elevation of Privilege

| ID | Threat | Attack Vector | Likelihood | Impact | Risk |
|----|--------|---------------|------------|--------|------|
| E-01 | Role Escalation | Participant gaining researcher access | Low | Critical | Medium |
| E-02 | Cross-Study Access | Researcher accessing unauthorized studies | Medium | High | Medium |
| E-03 | Admin API Access | Non-admin accessing admin endpoints | Low | Critical | Medium |
| E-04 | App Switching Abuse | Unauthorized app context switching | Low | High | Low |

**Existing Controls:**
- Role-based access control (Roles: SUPERADMIN, ADMIN, RESEARCHER, DEVELOPER, STUDY_COORDINATOR, ORG_ADMIN, WORKER)
- AuthEvaluator for permission checking
- Study-specific enrollment verification
- App switching restricted to Synapse-authenticated administrators
- Organization membership verification

---

## 5. Attack Surface Analysis

### 5.1 Entry Points

| Entry Point | Protocol | Authentication | Description |
|-------------|----------|----------------|-------------|
| `/v3/auth/*` | HTTPS | None/Session | Authentication endpoints |
| `/v3/participants/*` | HTTPS | Session + Role | Participant management |
| `/v3/uploads/*` | HTTPS | Session | Health data uploads |
| `/v3/consents/*` | HTTPS | Session | Consent operations |
| `/v3/apps/*` | HTTPS | Session + Admin | App configuration |
| `/v3/studies/*` | HTTPS | Session + Role | Study management |
| `/v3/oauth/*` | HTTPS | Session | OAuth token management |
| `/v1/crc/*` | HTTPS | Basic Auth | CRC (COVID-19) endpoints |
| S3 Presigned URLs | HTTPS | Presigned Token | Direct file upload/download |

### 5.2 External Dependencies

| Dependency | Purpose | Risk Level | Notes |
|------------|---------|------------|-------|
| Synapse | Data export & storage | High | Contains exported PHI |
| AWS RDS | Primary database | Critical | Contains PII/account data |
| AWS DynamoDB | NoSQL storage | Critical | Contains health records |
| AWS S3 | File storage | Critical | Contains encrypted uploads |
| AWS SES | Email delivery | Medium | Verification emails |
| AWS SNS | SMS delivery | Medium | Phone verification |
| AWS SQS | Message queuing | Medium | Async job processing |
| Redis (ElastiCache) | Session caching | High | Contains session tokens |
| GBF Medical | Lab orders | Medium | External lab integration |
| Google Geocoding API | Address validation | Low | CRC-specific |

### 5.3 Technologies and Frameworks

| Technology | Version/Type | Security Considerations |
|------------|--------------|------------------------|
| Java/Spring Boot | Java application | Keep dependencies updated |
| Hibernate | ORM | SQL injection prevention |
| JSoup | HTML parsing | XSS sanitization |
| BouncyCastle | Cryptography | Encryption implementation |
| AWS SDK | Cloud services | IAM permissions |

---

## 6. Security Controls Matrix

### 6.1 Authentication Controls

| Control | Implementation | Status |
|---------|----------------|--------|
| Password Hashing | BCRYPT (cost=12), PBKDF2 (250,000 iterations) | ✅ Active |
| Session Management | Redis-backed sessions with expiration | ✅ Active |
| Multi-Factor Auth | Email/Phone verification | ✅ Active |
| OAuth Integration | Synapse OAuth for federated auth | ✅ Active |
| Account Lockout | Timing-based protection against enumeration | ✅ Active |

### 6.2 Authorization Controls

| Control | Implementation | Status |
|---------|----------------|--------|
| Role-Based Access | AuthEvaluator with role hierarchy | ✅ Active |
| Study Enrollment | Enrollment verification for study access | ✅ Active |
| Organization Membership | Org-based access restrictions | ✅ Active |
| Consent Verification | Consent status checking | ✅ Active |

### 6.3 Data Protection Controls

| Control | Implementation | Status |
|---------|----------------|--------|
| Encryption at Rest | S3 server-side encryption (AES-256) | ✅ Active |
| Encryption in Transit | TLS/HTTPS mandatory | ✅ Active |
| Upload Encryption | CMS encryption for uploads | ✅ Active |
| Health Code Hashing | Configurable algorithm hashing | ✅ Active |
| Data Sharing Controls | SharingScope enforcement | ✅ Active |

### 6.4 Input Validation Controls

| Control | Implementation | Status |
|---------|----------------|--------|
| HTML Sanitization | JSoup with configurable safelists | ✅ Active |
| Email Validation | OWASP regex pattern | ✅ Active |
| Phone Validation | Phone number validation | ✅ Active |
| Upload Validation | MD5 checksum, size limits | ✅ Active |
| Parameter Validation | Spring validators | ✅ Active |

### 6.5 Rate Limiting Controls

| Control | Implementation | Status |
|---------|----------------|--------|
| Account Creation | ByteRateLimiter (3/5min prod) | ✅ Active |
| Email/SMS Verification | Channel throttling | ✅ Active |
| File Download | ByteRateLimiter (1MB/hour) | ✅ Active |
| API Requests | Application-level throttling | ⚠️ Partial |

### 6.6 Security Headers

| Header | Value | Purpose |
|--------|-------|---------|
| Content-Security-Policy | `default-src 'self' 'unsafe-inline' assets.sagebridge.org` | XSS mitigation |
| Strict-Transport-Security | `max-age=31536000; includeSubDomains` | Force HTTPS |
| X-Content-Type-Options | `nosniff` | Prevent MIME sniffing |
| X-Frame-Options | `DENY` | Clickjacking prevention |
| X-Permitted-Cross-Domain-Policies | `none` | Prevent cross-domain embedding |
| X-XSS-Protection | `1; mode=block` | Browser XSS filter |

---

## 7. Identified Vulnerabilities and Recommendations

### 7.1 High Priority

| ID | Vulnerability | Recommendation | Priority |
|----|---------------|----------------|----------|
| V-01 | Broad CORS configuration (`@CrossOrigin` without origin restrictions) | Implement explicit origin allowlist for all controllers | High |
| V-02 | S3 CORS allows all origins (`*`) for PUT operations | Restrict to known application origins | High |
| V-03 | Limited API-level rate limiting | Implement comprehensive API gateway rate limiting | High |

### 7.2 Medium Priority

| ID | Vulnerability | Recommendation | Priority |
|----|---------------|----------------|----------|
| V-04 | Basic Auth used in CRC controller | Migrate to token-based authentication | Medium |
| V-05 | `unsafe-inline` in Content-Security-Policy | Implement nonce-based CSP for inline scripts | Medium |
| V-06 | No virus scanning mentioned for uploads | Implement virus scanning for uploaded files (SNS topic exists but verify implementation) | Medium |
| V-07 | Participant enumeration possible via timing | Ensure consistent response times | Medium |

### 7.3 Low Priority

| ID | Vulnerability | Recommendation | Priority |
|----|---------------|----------------|----------|
| V-08 | Legacy password algorithms still supported | Plan migration to modern algorithms only | Low |
| V-09 | External geocoding API dependency | Consider caching or self-hosted alternative | Low |

---

## 8. Compliance Considerations

### 8.1 HIPAA Compliance

| Requirement | Status | Notes |
|-------------|--------|-------|
| Access Controls | ✅ | Role-based, study-based |
| Audit Controls | ⚠️ | Activity events exist, comprehensive audit trail recommended |
| Integrity Controls | ✅ | Checksums, versioning |
| Transmission Security | ✅ | TLS/HTTPS enforced |
| Encryption | ✅ | At rest and in transit |
| Unique User Identification | ✅ | Account system with unique IDs |
| Automatic Logoff | ✅ | Session expiration |

### 8.2 GDPR Considerations

| Requirement | Status | Notes |
|-------------|--------|-------|
| Data Minimization | ✅ | Health code pseudonymization |
| Right to Erasure | ⚠️ | Account deletion supported, verify data purging |
| Data Portability | ✅ | Export functionality exists |
| Consent Management | ✅ | Comprehensive consent system |
| Privacy by Design | ✅ | Sharing scope controls |

---

## 9. Risk Summary

### 9.1 Risk Heat Map

```
                    IMPACT
            Low     Medium    High     Critical
         ┌─────────┬─────────┬─────────┬─────────┐
  High   │         │  D-01   │         │         │
         │         │  D-03   │         │         │
         ├─────────┼─────────┼─────────┼─────────┤
LIKELIHOOD Medium │         │  I-06   │  E-02   │  S-01   │
         │         │  R-01   │  I-02   │  S-02   │
         │         │  R-03   │  I-03   │  I-01   │
         ├─────────┼─────────┼─────────┼─────────┤
  Low    │  T-05   │  D-02   │  T-01   │  E-01   │
         │  R-02   │  D-04   │  T-02   │  E-03   │
         │         │  D-05   │  T-04   │  I-05   │
         │         │         │  E-04   │  S-05   │
         │         │         │  I-04   │         │
         └─────────┴─────────┴─────────┴─────────┘
```

### 9.2 Top Risks

1. **Session Hijacking (S-01)** - Session tokens could be stolen via XSS or network interception
2. **Credential Stuffing (S-02)** - Reused passwords remain a significant threat
3. **PHI Exposure (I-01)** - Health data exposure would have severe regulatory consequences
4. **API Flood (D-01)** - DDoS attacks could disrupt research operations
5. **Cross-Study Access (E-02)** - Improper access control could leak study data

---

## 10. Recommendations Summary

### Immediate Actions (0-30 days)

1. Audit and restrict CORS configurations
2. Review and enhance API rate limiting
3. Verify virus scanning implementation for uploads
4. Review Synapse integration security

### Short-term Actions (30-90 days)

1. Implement comprehensive audit logging
2. Migrate CRC controller from Basic Auth
3. Enhance Content-Security-Policy
4. Security testing and penetration testing

### Long-term Actions (90+ days)

1. Implement API gateway with WAF
2. Migrate legacy password algorithms
3. Implement security monitoring and alerting
4. Regular threat model updates

---

## 11. References

### 11.1 External Documentation

- [Bridge Platform Developer Documentation](https://developer.sagebridge.org/)
- [Synapse Documentation](https://docs.synapse.org/)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/index.html)
- [OWASP Top 10](https://owasp.org/Top10/)

### 11.2 Internal References

| Repository | Description |
|------------|-------------|
| Sage-Bionetworks/BridgeServer2 | Core server code |
| Sage-Bionetworks/BridgeServer2-infra | Infrastructure |
| Sage-Bionetworks/Bridge-Exporter | Data exporter |
| Sage-Bionetworks/BridgeWorkerPlatform | Async workers |

---

## 12. Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | March 2026 | Security Analysis | Initial threat model |

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **PHI** | Protected Health Information - Health data protected under HIPAA |
| **PII** | Personally Identifiable Information - Data that can identify an individual |
| **Health Code** | Pseudonymous identifier for participants |
| **Sharing Scope** | Participant's data sharing preference |
| **Consent** | Participant's agreement to participate in research |
| **App** | A Bridge application representing a research study |
| **Synapse** | Sage Bionetworks' data sharing platform |

## Appendix B: STRIDE Categories

| Category | Description |
|----------|-------------|
| **S**poofing | Impersonating another user or system |
| **T**ampering | Unauthorized modification of data |
| **R**epudiation | Denying actions that were performed |
| **I**nformation Disclosure | Unauthorized access to data |
| **D**enial of Service | Making services unavailable |
| **E**levation of Privilege | Gaining unauthorized access rights |
