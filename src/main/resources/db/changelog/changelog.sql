--liquibase formatted sql

--changeset bridge:1

CREATE TABLE IF NOT EXISTS `Accounts` (
  `id` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `createdOn` bigint(20) NOT NULL,
  `healthCode` varchar(255) DEFAULT NULL,
  `healthId` varchar(255) DEFAULT NULL,
  `modifiedOn` bigint(20) NOT NULL,
  `firstName` varchar(255) DEFAULT NULL,
  `lastName` varchar(255) DEFAULT NULL,
  `passwordAlgorithm` enum('STORMPATH_HMAC_SHA_256','BCRYPT','PBKDF2_HMAC_SHA_256') DEFAULT NULL,
  `passwordHash` varchar(255) DEFAULT NULL,
  `passwordModifiedOn` bigint(20) NOT NULL,
  `status` enum('DISABLED','ENABLED','UNVERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  `version` int(10) unsigned NOT NULL DEFAULT '0',
  `clientData` mediumtext COLLATE utf8_unicode_ci,
  `phone` varchar(20) DEFAULT NULL,
  `phoneVerified` tinyint(1) DEFAULT NULL,
  `emailVerified` tinyint(1) DEFAULT NULL,
  `phoneRegion` varchar(2) DEFAULT NULL,
  `externalId` varchar(255) DEFAULT NULL,
  `timeZone` varchar(6) DEFAULT NULL,
  `sharingScope` enum('NO_SHARING','SPONSORS_AND_PARTNERS','ALL_QUALIFIED_RESEARCHERS') DEFAULT NULL,
  `notifyByEmail` tinyint(1) DEFAULT '1',
  `migrationVersion` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `Accounts-StudyId-Email-Index` (`studyId`,`email`),
  UNIQUE KEY `Accounts-StudyId-Phone-Index` (`studyId`,`phone`),
  UNIQUE KEY `Accounts-StudyId-ExternalId-Index` (`studyId`,`externalId`),
  UNIQUE KEY `Accounts-StudyId-HealthCode-Index` (`studyId`,`healthCode`),
  KEY `Accounts-StudyId-Index` (`studyId`),
  KEY `Accounts-HealthCode-Index` (`healthCode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountAttributes` (
  `accountId` varchar(255) NOT NULL,
  `attributeKey` varchar(255) NOT NULL,
  `attributeValue` varchar(255) NOT NULL,
  PRIMARY KEY (`accountId`,`attributeKey`),
  KEY `AccountAttributes-AccountId-Index` (`accountId`),
  CONSTRAINT `AccountAttributes-Id-Constraint` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountConsents` (
  `accountId` varchar(255) NOT NULL,
  `subpopulationGuid` varchar(255) NOT NULL,
  `signedOn` bigint(20) NOT NULL,
  `birthdate` varchar(255) DEFAULT NULL,
  `consentCreatedOn` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `signatureImageData` mediumtext,
  `signatureImageMimeType` varchar(255) DEFAULT NULL,
  `withdrewOn` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`accountId`,`subpopulationGuid`,`signedOn`),
  KEY `AccountConsents-AccountId-SubpopGuid-Index` (`accountId`,`subpopulationGuid`),
  KEY `AccountConsents-AccountId-Index` (`accountId`),
  CONSTRAINT `AccountConsents-Id-Constrant` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountDataGroups` (
  `accountId` varchar(255) NOT NULL,
  `dataGroup` varchar(255) NOT NULL,
  PRIMARY KEY (`accountId`,`dataGroup`),
  KEY `AccountDataGroups-AccountId-Index` (`accountId`),
  CONSTRAINT `AccountDataGroups-Id-Constraint` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountLanguages` (
  `accountId` varchar(255) NOT NULL,
  `language` varchar(255) NOT NULL,
  PRIMARY KEY (`accountId`,`language`),
  KEY `AccountLanguages-AccountId-Index` (`accountId`),
  CONSTRAINT `AccountLanguages-Id-Constraint` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountRoles` (
  `accountId` varchar(255) NOT NULL,
  `role` enum('DEVELOPER','RESEARCHER','ADMIN','TEST_USERS','WORKER') NOT NULL,
  PRIMARY KEY (`accountId`,`role`),
  KEY `AccountRoles-AccountId-Index` (`accountId`),
  CONSTRAINT `AccountRoles-Id-Constraint` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountSecrets` (
  `accountId` varchar(255) NOT NULL,
  `hash` varchar(255) NOT NULL,
  `type` enum('REAUTH') DEFAULT 'REAUTH',
  `algorithm` enum('STORMPATH_HMAC_SHA_256','BCRYPT','PBKDF2_HMAC_SHA_256') COLLATE utf8_unicode_ci NOT NULL,
  `createdOn` bigint(20) NOT NULL,
  PRIMARY KEY (`accountId`,`hash`),
  KEY `secrets_idx` (`accountId`,`type`),
  CONSTRAINT `fk_account_secrets` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `Substudies` (
  `id` varchar(60) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `version` int(10) unsigned NOT NULL DEFAULT '0',
  `deleted` tinyint(1) DEFAULT '0',
  `createdOn` bigint(20) NOT NULL,
  `modifiedOn` bigint(20) NOT NULL,
  PRIMARY KEY (`id`,`studyId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `AccountsSubstudies` (
  `studyId` varchar(60) NOT NULL,
  `substudyId` varchar(60) NOT NULL,
  `accountId` varchar(255) NOT NULL,
  `externalId` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`studyId`,`substudyId`,`accountId`),
  KEY `fk_substudy` (`substudyId`,`studyId`),
  KEY `fk_account2` (`accountId`),
  CONSTRAINT `fk_account2` FOREIGN KEY (`accountId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_substudy` FOREIGN KEY (`substudyId`, `studyId`) REFERENCES `Substudies` (`id`, `studyId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `SharedModuleMetadata` (
  `id` varchar(60) NOT NULL,
  `licenseRestricted` tinyint(4) NOT NULL DEFAULT '0',
  `name` varchar(255) DEFAULT NULL,
  `notes` text,
  `os` varchar(60) DEFAULT NULL,
  `published` tinyint(4) NOT NULL DEFAULT '0',
  `schemaId` varchar(60) DEFAULT NULL,
  `schemaRevision` int(10) unsigned DEFAULT NULL,
  `surveyCreatedOn` bigint(20) unsigned DEFAULT NULL,
  `surveyGuid` varchar(36) DEFAULT NULL,
  `version` int(10) unsigned NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `SharedModuleTags` (
  `id` varchar(60) NOT NULL,
  `tag` varchar(255) NOT NULL,
  `version` int(10) unsigned NOT NULL,
  KEY `MetadataKey_idx` (`id`,`version`),
  CONSTRAINT `MetadataKey` FOREIGN KEY (`id`, `version`) REFERENCES `SharedModuleMetadata` (`id`, `version`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `Templates` (
  `guid` varchar(60) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `templateType` enum('EMAIL_ACCOUNT_EXISTS','EMAIL_APP_INSTALL_LINK','EMAIL_RESET_PASSWORD','EMAIL_SIGN_IN','EMAIL_SIGNED_CONSENT','EMAIL_VERIFY_EMAIL','SMS_ACCOUNT_EXISTS','SMS_APP_INSTALL_LINK','SMS_PHONE_SIGN_IN','SMS_RESET_PASSWORD','SMS_SIGNED_CONSENT','SMS_VERIFY_PHONE') NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `description` text,
  `createdOn` bigint(20) unsigned DEFAULT NULL,
  `modifiedOn` bigint(20) unsigned DEFAULT NULL,
  `publishedCreatedOn` bigint(20) unsigned DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int(10) unsigned NOT NULL,
  PRIMARY KEY (`guid`),
  KEY `type_set_idx` (`studyId`,`templateType`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--changeset bridge:2

CREATE TABLE IF NOT EXISTS `TemplateRevisions` (
  `templateGuid` VARCHAR(60) NOT NULL,
  `createdOn` BIGINT UNSIGNED NOT NULL,
  `createdBy` VARCHAR(255) NOT NULL,
  `storagePath` VARCHAR(255) NOT NULL,
  `subject` VARCHAR(255) NULL,
  `mimeType` ENUM('HTML', 'TEXT', 'PDF') NOT NULL,
  PRIMARY KEY (`templateGuid`, `createdOn`),
  CONSTRAINT `Templates-Guid-Constraint` FOREIGN KEY (`templateGuid`) REFERENCES `Templates` (`guid`) ON DELETE CASCADE
) CHARACTER SET utf8 COLLATE utf8_unicode_ci;

--changeset bridge:3

ALTER TABLE `Accounts`
CHANGE COLUMN `passwordAlgorithm` `passwordAlgorithm` ENUM('STORMPATH_HMAC_SHA_256', 'BCRYPT', 'PBKDF2_HMAC_SHA_256',
  'STORMPATH_PBKDF2_DOUBLE_HASH') DEFAULT NULL;

ALTER TABLE `AccountSecrets`
CHANGE COLUMN `algorithm` `algorithm` ENUM('STORMPATH_HMAC_SHA_256', 'BCRYPT', 'PBKDF2_HMAC_SHA_256',
  'STORMPATH_PBKDF2_DOUBLE_HASH') NOT NULL;

--changeset bridge:4

ALTER TABLE `Accounts`
ADD COLUMN `stormpathPasswordHash` varchar(255) DEFAULT NULL;
  
-- changeset bridge:5

CREATE TABLE IF NOT EXISTS `RequestInfos` (
  `userId` varchar(255) NOT NULL,
  `clientInfo` varchar(255),
  `userAgent` varchar(255),
  `languages` varchar(255),
  `userDataGroups` varchar(255),
  `userSubstudyIds` varchar(255),
  `activitiesAccessedOn` varchar(255),
  `signedInOn` varchar(255),
  `uploadedOn` varchar(255),
  `timeZone` varchar(255),
  `studyIdentifier` varchar(255) NOT NULL,
  PRIMARY KEY (`userId`),
  CONSTRAINT `RequestInfo-UserId-Constraint` FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8 COLLATE utf8_unicode_ci;

-- changeset bridge:6

ALTER TABLE `AccountLanguages`
ADD COLUMN `order_index` int(8) DEFAULT 0;

-- changeset bridge:7

CREATE TABLE IF NOT EXISTS `FileMetadata` (
    `studyId` varchar(255) NOT NULL,
    `guid` varchar(60) NOT NULL,
    `name` varchar(255) DEFAULT NULL,
    `createdOn` BIGINT UNSIGNED NOT NULL,
    `modifiedOn` BIGINT UNSIGNED NOT NULL,
    `description` text,
    `mimeType` varchar(255) DEFAULT NULL,
    `deleted` tinyint(1) NOT NULL DEFAULT '0',
    `version` int(10) unsigned NOT NULL,
    PRIMARY KEY (`guid`),
    KEY `Studies_idx` (`studyId`),
    KEY `studyId_guid_idx` (`studyId`,`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `FileRevisions` (
    `fileGuid` varchar(60) NOT NULL, 
    `createdOn` BIGINT UNSIGNED NOT NULL,
    `description` text,
    `uploadURL` VARCHAR(512) DEFAULT NULL,
    `status` ENUM('PENDING','AVAILABLE') NOT NULL,
    PRIMARY KEY (`fileGuid`, `createdOn`),
    CONSTRAINT `FileMetadata-Guid-Constraint` FOREIGN KEY (`fileGuid`) REFERENCES `FileMetadata` (`guid`) ON DELETE CASCADE
) CHARACTER SET utf8 COLLATE utf8_unicode_ci;

-- changeset bridge:8

ALTER TABLE `Accounts`
DROP COLUMN `stormpathPasswordHash`;

-- changeset bridge:9

ALTER TABLE `FileMetadata`
DROP COLUMN `mimeType`;

ALTER TABLE `FileRevisions`
ADD COLUMN `mimeType` varchar(255) DEFAULT NULL,
ADD COLUMN `name` varchar(255) DEFAULT NULL,
ADD COLUMN `size` bigint(20) DEFAULT NULL,
MODIFY COLUMN `uploadURL` VARCHAR(1024) DEFAULT NULL;

-- changeset bridge:10

ALTER TABLE `Accounts`
ADD COLUMN `synapseUserId` varchar(255) DEFAULT NULL,
ADD UNIQUE KEY `Accounts-StudyId-SynapseUserId-Index` (`studyId`,`synapseUserId`);

-- changeset bridge:11

CREATE INDEX `Accounts-SynapseUserId-Index` ON `Accounts`(`synapseUserId`);

-- changeset bridge:12

ALTER TABLE `AccountRoles`
MODIFY COLUMN `role` enum('DEVELOPER','RESEARCHER','ADMIN','TEST_USERS','WORKER','SUPERADMIN') NOT NULL;

-- changeset bridge:13

CREATE TABLE `Assessments` (
  `guid` varchar(255) NOT NULL,
  `appId` varchar(255) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `revision` int(10) unsigned NOT NULL,
  `ownerId` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `summary` text,
  `validationStatus` text,
  `normingStatus` text,
  `osName` varchar(255) NOT NULL,
  `originGuid` varchar(255),
  `customizationFields` text,
  `createdOn` bigint(20) unsigned DEFAULT NULL,
  `modifiedOn` bigint(20) unsigned DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` bigint(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`guid`),
  UNIQUE KEY (`appId`, `identifier`, `revision`),
  CONSTRAINT FOREIGN KEY (`originGuid`) REFERENCES `Assessments` (`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE INDEX `Assessments-AppId` ON `Assessments`(`appId`);
CREATE INDEX `Assessments-AppId-Guid` ON `Assessments`(`appId`, `guid`);
CREATE INDEX `Assessments-AppId-Identifier` ON `Assessments`(`appId`, `identifier`);
-- the appId-identifier-revision combo is indexed by UNIQUE KEY

CREATE TABLE `Tags` (
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `AssessmentTags` (
  `assessmentGuid` varchar(255) NOT NULL,
  `tagValue` varchar(255) NOT NULL,
  PRIMARY KEY (`assessmentGuid`, `tagValue`),
  CONSTRAINT FOREIGN KEY (`assessmentGuid`) REFERENCES `Assessments` (`guid`),
  CONSTRAINT FOREIGN KEY (`tagValue`) REFERENCES `Tags` (`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE INDEX `AssessmentTags-TagValue` ON `AssessmentTags`(`tagValue`);

-- changeset bridge:14

CREATE TABLE `ExternalResources` (
  `appId` varchar(255) NOT NULL,
  `guid` varchar(255) NOT NULL,
  `assessmentId` varchar(255),
  `title` varchar(255) NOT NULL,
  `url` text NOT NULL,
  `format` varchar(255),
  `date` varchar(255),
  `description` text,
  `language` varchar(255),
  `category` enum('CUSTOMIZATION_OPTIONS', 'DATA_REPOSITORY', 
    'SCIENCE_DOCUMENTATION', 'DEVELOPER_DOCUMENTATION', 'LICENSE', 
    'PUBLICATION', 'RELEASE_NOTE', 'SAMPLE_APP', 'SAMPLE_DATA', 
    'SCREENSHOT', 'SEE_ALSO', 'USED_IN_STUDY', 'WEBSITE', 
    'OTHER') NOT NULL,
  `contributors` text,
  `creators` text,
  `publishers` text,
  `minRevision` int(10),
  `maxRevision` int(10),
  `createdAtRevision` int(10) NOT NULL,
  `createdOn` bigint(20) unsigned DEFAULT NULL,
  `modifiedOn` bigint(20) unsigned DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0' NOT NULL,
  `version` bigint(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`appId`, `guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE INDEX `ExternalResources-AppId-AssessmentId` ON `ExternalResources`(`appId`, `assessmentId`);

-- changeset bridge:15

CREATE TABLE `AssessmentConfigs` (
  `guid` varchar(255) NOT NULL,
  `config` text,
  `createdOn` bigint(20) unsigned NOT NULL,
  `modifiedOn` bigint(20) unsigned NOT NULL,
  `version` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:16

CREATE TABLE `Organizations` (
  `appId` varchar(255) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `name` varchar(255),
  `description` text,
  `createdOn` bigint(20) unsigned NOT NULL,
  `modifiedOn` bigint(20) unsigned NOT NULL,
  `version` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`appId`, `identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:17

ALTER TABLE `Accounts`
ADD COLUMN `orgMembership` varchar(255),
ADD CONSTRAINT FOREIGN KEY (`studyId`, `orgMembership`) REFERENCES `Organizations` (`appId`, `identifier`);

CREATE INDEX `Accounts-OrgMembership` ON `Accounts` (`studyId`, `orgMembership`);

-- changeset bridge:18

-- Sponsors. Named consistent with other associative tables.
CREATE TABLE IF NOT EXISTS `OrganizationsStudies` (
  `appId` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `orgId` varchar(255) NOT NULL,
  PRIMARY KEY (`appId`,`studyId`,`orgId`),
  CONSTRAINT `fk_os_organization` FOREIGN KEY (`appId`, `orgId`) REFERENCES `Organizations` (`appId`, `identifier`) ON DELETE CASCADE,
  CONSTRAINT `fk_os_study` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Fix this (one value is missing).
ALTER TABLE `ExternalResources`
MODIFY COLUMN `category` enum('CUSTOMIZATION_OPTIONS', 'DATA_REPOSITORY', 
    'SCIENCE_DOCUMENTATION', 'DEVELOPER_DOCUMENTATION', 'LICENSE', 
    'PUBLICATION', 'RELEASE_NOTE', 'SAMPLE_APP', 'SAMPLE_DATA', 
    'SCREENSHOT', 'VIDEO_PREVIEW', 'SEE_ALSO', 'USED_IN_STUDY', 'WEBSITE', 
    'OTHER') NOT NULL;
    
-- changeset bridge:19

ALTER TABLE `AccountsSubstudies`
ADD COLUMN `consentRequired` tinyint(1) DEFAULT '0',
ADD COLUMN `enrolledOn` bigint(20),
ADD COLUMN `withdrawnOn` bigint(20),
ADD COLUMN `enrolledBy` varchar(255),
ADD COLUMN `withdrawnBy` varchar(255),
ADD COLUMN `withdrawalNote` varchar(255),
ADD CONSTRAINT `fk_enrolledBy` FOREIGN KEY (`enrolledBy`) REFERENCES `Accounts` (`id`),
ADD CONSTRAINT `fk_withdrawnBy` FOREIGN KEY (`withdrawnBy`) REFERENCES `Accounts` (`id`);

-- changeset bridge:20

ALTER TABLE `Assessments`
ADD COLUMN `minutesToComplete` int(10) DEFAULT NULL;

-- changeset bridge:21

-- This constraint was enforced in DynamoDB table, and is now enforced in associative table
-- an externalId must be unique in the context of an app.
ALTER TABLE AccountsSubstudies
ADD CONSTRAINT `unique_extId` UNIQUE (studyId, externalId);

-- changeset bridge:22

ALTER TABLE `AccountRoles`
MODIFY COLUMN `role` enum('DEVELOPER','RESEARCHER','ADMIN','ORG_ADMIN','WORKER','SUPERADMIN') NOT NULL;

-- changeset bridge:23

ALTER TABLE `AccountRoles`
MODIFY COLUMN `role` enum('DEVELOPER','RESEARCHER','ADMIN','ORG_ADMIN','WORKER','SUPERADMIN','STUDY_COORDINATOR') NOT NULL;

-- changeset bridge:24

ALTER TABLE `Substudies`
ADD COLUMN `clientData` mediumtext COLLATE utf8_unicode_ci;

-- changeset bridge:25

ALTER TABLE `AccountRoles`
MODIFY COLUMN `role` enum('DEVELOPER','RESEARCHER','ADMIN','ORG_ADMIN','WORKER','SUPERADMIN','STUDY_COORDINATOR','STUDY_DESIGNER') NOT NULL;

-- changeset bridge:26

ALTER TABLE `Assessments`
ADD COLUMN `labels` text DEFAULT NULL,
ADD COLUMN `colorScheme` text DEFAULT NULL;

ALTER TABLE `ExternalResources`
MODIFY COLUMN `category` enum('CUSTOMIZATION_OPTIONS', 'DATA_REPOSITORY', 
    'SCIENCE_DOCUMENTATION', 'DEVELOPER_DOCUMENTATION', 'LICENSE', 
    'PUBLICATION', 'RELEASE_NOTE', 'SAMPLE_APP', 'SAMPLE_DATA', 
    'SCREENSHOT', 'SEE_ALSO', 'USED_IN_STUDY', 'WEBSITE', 
    'OTHER', 'ICON') NOT NULL;

-- changeset bridge:27

CREATE TABLE `Schedules` (
  `appId` varchar(255) NOT NULL,
  `ownerId` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `guid` varchar(60) NOT NULL,
  `duration` varchar(60) NOT NULL,
  `clientData` mediumtext COLLATE utf8_unicode_ci,
  `published` tinyint(1) NOT NULL DEFAULT '0',
  `createdOn` bigint(20) unsigned DEFAULT NULL,
  `modifiedOn` bigint(20) unsigned DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `version` int(10) unsigned NOT NULL,
  PRIMARY KEY (`guid`),
  KEY `Schedules_appId_guid_idx` (`appId`,`guid`),
  KEY `Schedules_ownerId_guid_idx` (`ownerId`,`guid`),
  CONSTRAINT `Schedule-Organization-Constraint` FOREIGN KEY (`appId`, `ownerId`) REFERENCES `Organizations` (`appId`, `identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `Sessions` (
  `scheduleGuid` varchar(60) NOT NULL,
  `guid` varchar(60) NOT NULL,
  `position` int(10) signed,
  `name` varchar(255) NOT NULL,
  `startEventId` varchar(255) NOT NULL,
  `delayPeriod` varchar(60),
  `occurrences` int(10) unsigned,
  `intervalPeriod` varchar(60),
  `reminderPeriod` varchar(60),
  `messages` text DEFAULT NULL,
  `labels` text DEFAULT NULL,
  `performanceOrder` enum('PARTICIPANT_CHOICE','SEQUENTIAL','RANDOMIZED'),
  `notifyAt` enum('PARTICIPANT_CHOICE','START_OF_WINDOW','RANDOM'),
  `remindAt` enum('AFTER_WINDOW_START','BEFORE_WINDOW_END'),
  `allowSnooze` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`guid`),
  UNIQUE KEY `Session-guid-scheduleGuid-idx` (`guid`,`scheduleGuid`),
  CONSTRAINT `Session-Schedule-Constraint` FOREIGN KEY (`scheduleGuid`) REFERENCES `Schedules` (`guid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `SessionTimeWindows` (
  `sessionGuid` varchar(60) NOT NULL,
  `guid` varchar(60) NOT NULL,
  `position` int(10) signed,
  `startTime` varchar(60) NOT NULL,
  `expirationPeriod` varchar(60),
  `persistent` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`guid`),
  UNIQUE KEY `TimeWindow-guid-sessionGuid-idx` (`guid`,`sessionGuid`),
  CONSTRAINT `TimeWindow-Session-Constraint` FOREIGN KEY (`sessionGuid`) REFERENCES `Sessions` (`guid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `SessionAssessments` (
  `sessionGuid` varchar(60) NOT NULL,
  `position` int(10) signed,
  `appId` varchar(255) NOT NULL,
  `guid` varchar(60) NOT NULL,
  `identifier` varchar(255),
  `title` varchar(255),
  `minutesToComplete` int(10),
  `labels` text,
  `colorScheme` text,
  PRIMARY KEY (`sessionGuid`, `position`),
  CONSTRAINT `AssessmentRef-Session-Constraint` FOREIGN KEY (`sessionGuid`) REFERENCES `Sessions` (`guid`) ON DELETE CASCADE,
  CONSTRAINT `AssessmentRef-Assessment-Constraint` FOREIGN KEY (`guid`) REFERENCES `Assessments` (`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:28

ALTER TABLE `SessionAssessments`
ADD COLUMN `revision` int(10) unsigned;

CREATE TABLE `TimelineMetadata` (
  `guid` varchar(60) NOT NULL,
  `assessmentInstanceGuid` varchar(60),
  `assessmentGuid` varchar(60),
  `assessmentId` varchar(255),
  `assessmentRevision` int(10) unsigned,
  `sessionInstanceGuid` varchar(60) NOT NULL,
  `sessionGuid` varchar(60) NOT NULL,
  `scheduleGuid` varchar(60) NOT NULL,
  `schedulePublished` tinyint(1) NOT NULL,
  `scheduleModifiedOn` bigint(20) unsigned NOT NULL,
  `appId` varchar(255) NOT NULL,
  PRIMARY KEY (`guid`),
  CONSTRAINT `TimelineMetadata-Schedule-Constraint` FOREIGN KEY (`scheduleGuid`) REFERENCES `Schedules` (`guid`) ON DELETE CASCADE,
  CONSTRAINT `TimelineMetadata-Session-Constraint` FOREIGN KEY (`sessionGuid`) REFERENCES `Sessions` (`guid`) ON DELETE CASCADE,
  CONSTRAINT `TimelineMetadata-Assessment-Constraint` FOREIGN KEY (`assessmentGuid`) REFERENCES `Assessments` (`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:29

ALTER TABLE `Substudies`
ADD COLUMN `phase` enum('LEGACY', 'DESIGN', 'PUBLIC_RECRUITMENT', 'ENROLL_BY_INVITATION', 'IN_FLIGHT', 'ANALYSIS', 'COMPLETED', 'WITHDRAWN') DEFAULT 'LEGACY',
ADD COLUMN `details` varchar(510) DEFAULT NULL,
ADD COLUMN `studyLogoUrl` varchar(255) DEFAULT NULL,
ADD COLUMN `colorScheme` text DEFAULT NULL,
ADD COLUMN `institutionId` varchar(255) DEFAULT NULL,
ADD COLUMN `irbProtocolId` varchar(255) DEFAULT NULL,
ADD COLUMN `irbApprovedOn` varchar(12) DEFAULT NULL,
ADD COLUMN `irbApprovedUntil` varchar(12) DEFAULT NULL,
ADD COLUMN `scheduleGuid` varchar(255) DEFAULT NULL,
ADD COLUMN `disease` varchar(255) DEFAULT NULL,
ADD COLUMN `studyDesignType` varchar(255) DEFAULT NULL;

ALTER TABLE `Substudies`
ADD CONSTRAINT `Substudies-Schedule-Constraint` FOREIGN KEY (`scheduleGuid`) REFERENCES `Schedules` (`guid`) ON DELETE RESTRICT;

CREATE TABLE `StudyContacts` (
  `appId` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `role` enum('IRB','PRINCIPAL_INVESTIGATOR','INVESTIGATOR','SPONSOR','STUDY_SUPPORT','TECHNICAL_SUPPORT') NOT NULL,
  `position` varchar(255),
  `affiliation` varchar(255),
  `jurisdiction` varchar(255),
  `email` varchar(255),
  `phone` varchar(20),
  `phoneRegion` varchar(2),
  `placeName` varchar(255),
  `street` varchar(255),
  `division` varchar(255),
  `mailRouting` varchar(255),
  `city` varchar(255),
  `state` varchar(255),
  `postalCode` varchar(50),
  `country` varchar(255),
  `pos` int(10) signed,
  PRIMARY KEY (`appId`, `studyId`, `name`),
  CONSTRAINT `StudyContact-Study-Constraint` FOREIGN KEY (`studyId`,`appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:30

ALTER TABLE `TimelineMetadata`
ADD COLUMN `sessionStartEventId` varchar(255) NOT NULL,
ADD COLUMN `timeWindowGuid` varchar(60) NOT NULL,
ADD COLUMN `sessionInstanceStartDay` int(10) NOT NULL,
ADD COLUMN `sessionInstanceEndDay` int(10) NOT NULL;

-- changeset bridge:31

ALTER TABLE `StudyContacts`
DROP PRIMARY KEY,
ADD CONSTRAINT PRIMARY KEY (`appId`, `studyId`, `pos`);

-- changeset bridge:32

CREATE TABLE `AdherenceRecords` (
  `userId` varchar(255) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `instanceGuid` varchar(128) NOT NULL,
  `startedOn` bigint(20) unsigned NOT NULL,
  `eventTimestamp` bigint(20) unsigned,
  `finishedOn` bigint(20) unsigned,
  `uploadedOn` bigint(20) unsigned,
  `clientData` text COLLATE utf8_unicode_ci,
  `clientTimeZone` varchar(255),
  PRIMARY KEY (`userId`, `studyId`, `instanceGuid`, `startedOn`),
  CONSTRAINT `AdherenceRecord-Account-Constraint` FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `AdherenceRecord-Study-Constraint` FOREIGN KEY (`studyId`) REFERENCES `Substudies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:33

DROP TABLE `AdherenceRecords`;

CREATE TABLE `AdherenceRecords` (
  `appId` varchar(255) NOT NULL,
  `userId` varchar(255) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `instanceGuid` varchar(128) NOT NULL,
  `startedOn` bigint(20) unsigned NOT NULL,
  `eventTimestamp` bigint(20) unsigned,
  `finishedOn` bigint(20) unsigned,
  `uploadedOn` bigint(20) unsigned,
  `clientData` text COLLATE utf8_unicode_ci,
  `clientTimeZone` varchar(255),
  PRIMARY KEY (`userId`, `studyId`, `instanceGuid`, `startedOn`),
  KEY `AdherenceRecord-appId-studyId` (`appId`,`studyId`),
  CONSTRAINT `AdherenceRecord-Account-Constraint` FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `AdherenceRecord-Study-Constraint` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE `StudyActivityEvents` (
  `appId` varchar(255) NOT NULL,
  `userId` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `eventId` varchar(255) NOT NULL,
  `eventTimestamp` bigint(20) unsigned NOT NULL,
  `answerValue` varchar(255),
  `clientTimeZone` varchar(255),
  `createdOn` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`userId`, `studyId`, `eventId`, `eventTimestamp`),
  CONSTRAINT `StudyActivityEvent-Account-Constraint` FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `StudyActivityEvent-Study-Constraint` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:34

ALTER TABLE `Sessions`
DROP COLUMN `reminderPeriod`,
DROP COLUMN `messages`,
DROP COLUMN `notifyAt`,
DROP COLUMN `remindAt`,
DROP COLUMN `allowSnooze`;

-- changeset bridge:35

CREATE TABLE `SessionNotifications` (
  `sessionGuid` varchar(60) NOT NULL,
  `position` int(10) signed,
  `notifyAt` enum('AFTER_WINDOW_START','BEFORE_WINDOW_END'),
  `offsetPeriod` varchar(60),
  `intervalPeriod` varchar(60),
  `messages` text DEFAULT NULL,
  `allowSnooze` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`sessionGuid`, `position`),
  CONSTRAINT `SessionNotifications-Session-Constraint` FOREIGN KEY (`sessionGuid`) REFERENCES `Sessions` (`guid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:36

ALTER TABLE `Accounts`
ADD COLUMN `note` text;

-- changeset bridge:37

ALTER TABLE `Substudies`
DROP COLUMN `irbApprovedOn`,
DROP COLUMN `irbApprovedUntil`,
ADD COLUMN `irbName` varchar(60) DEFAULT NULL,
ADD COLUMN `irbProtocolName` varchar(512) DEFAULT NULL,
ADD COLUMN `irbDecisionOn` varchar(12) DEFAULT NULL,
ADD COLUMN `irbExpiresOn` varchar(12) DEFAULT NULL,
ADD COLUMN `irbDecisionType` enum('EXEMPT','APPROVED') DEFAULT NULL;

ALTER TABLE `TimelineMetadata`
ADD COLUMN `timeWindowPersistent` tinyint(1) DEFAULT 0;

DROP TABLE `AdherenceRecords`;

CREATE TABLE `AdherenceRecords` (
  `appId` varchar(255) NOT NULL,
  `userId` varchar(255) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `instanceGuid` varchar(128) NOT NULL,
  `startedOn` bigint(20) unsigned,
  `eventTimestamp` bigint(20) unsigned,
  `finishedOn` bigint(20) unsigned,
  `uploadedOn` bigint(20) unsigned,
  `instanceTimestamp` bigint(20) unsigned,
  `clientData` text COLLATE utf8_unicode_ci,
  `clientTimeZone` varchar(255),
  `declined` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`userId`, `studyId`, `instanceGuid`, `eventTimestamp`, `instanceTimestamp`),
  CONSTRAINT `AdherenceRecord-Account-Constraint` FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `AdherenceRecord-Study-Constraint` FOREIGN KEY (`studyId`) REFERENCES `Substudies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:38

ALTER TABLE `RequestInfos`
ADD COLUMN `timelineAccessedOn` varchar(255);

-- changeset bridge:39

ALTER TABLE `Substudies`
DROP COLUMN `disease`,
DROP COLUMN `studyDesignType`,
ADD COLUMN `signInTypes` text,
ADD COLUMN `keywords` varchar(255);

CREATE TABLE IF NOT EXISTS `StudyDiseases` (
  `appId` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `disease` varchar(255) NOT NULL,
  PRIMARY KEY (`appId`, `studyId`, `disease`),
  CONSTRAINT `StudyDiseases-Study-Constraint` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `StudyDesignTypes` (
  `appId` varchar(255) NOT NULL,
  `studyId` varchar(255) NOT NULL,
  `designType` varchar(255) NOT NULL,
  PRIMARY KEY (`appId`, `studyId`, `designType`),
  CONSTRAINT `StudyDesignTypes-Study-Constraint` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:40

ALTER TABLE `FileMetadata`
ADD COLUMN `disposition` enum('INLINE','ATTACHMENT') NOT NULL DEFAULT 'ATTACHMENT';

ALTER TABLE `Substudies`
ADD COLUMN `logoGuid` varchar(255);

-- changeset bridge:41

ALTER TABLE `Substudies`
MODIFY COLUMN `phase` enum('LEGACY', 'DESIGN', 'RECRUITMENT', 'IN_FLIGHT', 'ANALYSIS', 'COMPLETED', 'WITHDRAWN') DEFAULT 'LEGACY';

-- changeset bridge:42

CREATE TABLE `StudyCustomEvents` (
  `appId` varchar(60) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `eventId` varchar(255) NOT NULL,
  `pos` int(10) signed,
  `updateType` enum('MUTABLE', 'IMMUTABLE', 'FUTURE_ONLY') DEFAULT 'IMMUTABLE',
  PRIMARY KEY (`appId`, `studyId`, `eventId`),
  CONSTRAINT `StudyCustomEvents-Study-Constraint` FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:43

ALTER TABLE `Accounts`
ADD COLUMN `clientTimeZone` varchar(64) DEFAULT NULL;

-- changeset bridge:44

ALTER TABLE `Sessions`
DROP COLUMN `startEventId`;

CREATE TABLE `SessionStartEvents` (
  `sessionGuid` varchar(60) NOT NULL,
  `position` int(10) signed,
  `eventId` varchar(255),
  PRIMARY KEY (`sessionGuid`, `position`),
  CONSTRAINT `SessionEvents-Session-Constraint` FOREIGN KEY (`sessionGuid`) REFERENCES `Sessions` (`guid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:46

ALTER TABLE `TimelineMetadata` 
ADD INDEX `TimelineMetadata-ScheduleGuid` (scheduleGuid);

-- changeset bridge:47

ALTER TABLE `AccountsSubstudies` 
DROP FOREIGN KEY `fk_substudy`;

ALTER TABLE `AccountsSubstudies`
ADD CONSTRAINT `fk_substudy` FOREIGN KEY (`substudyId`, `studyId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE;

-- changeset bridge:48

CREATE TABLE `ScheduleStudyBursts` (
  `scheduleGuid` varchar(60) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `originEventId` varchar(255) NOT NULL,
  `intervalPeriod` varchar(60),
  `occurrences` int(10) unsigned,
  `updateType` enum('MUTABLE', 'IMMUTABLE', 'FUTURE_ONLY') DEFAULT 'IMMUTABLE',
  `position` int(10) signed,
  PRIMARY KEY (`scheduleGuid`, `identifier`),
  CONSTRAINT `ScheduleStudyBursts-Schedule-Constraint` FOREIGN KEY (`scheduleGuid`) REFERENCES `Schedules` (`guid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `Sessions`
ADD COLUMN `studyBurstIds` varchar(512);

-- changeset bridge:49

ALTER TABLE `Sessions`
ADD COLUMN `symbol` varchar(32);

-- changeset bridge:50

ALTER TABLE `AccountsSubstudies`
ADD COLUMN `note` text DEFAULT NULL;

-- changeset bridge:52

ALTER TABLE `StudyActivityEvents`
ADD COLUMN `studyBurstId` varchar(255),
ADD COLUMN `originEventId` varchar(255),
ADD COLUMN `periodFromOrigin` varchar(60);

-- changeset bridge:53

ALTER TABLE `StudyActivityEvents`
ADD COLUMN `updateType` enum('MUTABLE', 'IMMUTABLE', 'FUTURE_ONLY') DEFAULT 'IMMUTABLE';

-- changeset bridge:54

ALTER TABLE `ScheduleStudyBursts`
ADD COLUMN `delayPeriod` varchar(60);

-- changeset bridge:55

ALTER TABLE `TimelineMetadata`
ADD COLUMN `studyBurstId` varchar(255),
ADD COLUMN `studyBurstNum` int(10) unsigned;

-- changeset bridge:56

ALTER TABLE `Substudies`
ADD COLUMN `studyTimeZone` varchar(255),
ADD COLUMN `adherenceThresholdPercentage` int(3) unsigned;

-- changeset bridge:57

ALTER TABLE `Sessions` 
MODIFY `symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `TimelineMetadata`
ADD COLUMN `sessionSymbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
ADD COLUMN `sessionName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- changeset bridge:58

ALTER TABLE StudyContacts
DROP COLUMN state;

-- changeset bridge:59

CREATE TABLE IF NOT EXISTS `WeeklyAdherenceReports` (
  `appId` varchar(60) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `userId` varchar(255) NOT NULL,
  `participant` text NOT NULL,
  `testAccount` tinyint(1) DEFAULT 0,
  `clientTimeZone` varchar(255),
  `createdOn` bigint(20) NOT NULL,
  `weeklyAdherencePercent` int(3) signed,
  `nextActivity` text,
  `byDayEntries` mediumtext,
  PRIMARY KEY (`appId`, `studyId`, `userId`),
  FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `WeeklyAdherenceReportLabels` (
  `appId` varchar(60) NOT NULL,
  `studyId` varchar(60) NOT NULL,
  `userId` varchar(255) NOT NULL,
  `label` varchar(2048) NOT NULL,
  PRIMARY KEY (`appId`, `studyId`, `userId`, `label`(255)),
  KEY `WeeklyAdherenceReportLabels-Index` (`appId`, `studyId`, `userId`),
  FOREIGN KEY (`userId`) REFERENCES `Accounts` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`studyId`, `appId`) REFERENCES `Substudies` (`id`, `studyId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- changeset bridge:60

ALTER TABLE `WeeklyAdherenceReports`
ADD COLUMN `rows` mediumtext;
