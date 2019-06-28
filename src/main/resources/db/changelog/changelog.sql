--liquibase formatted sql

--changeset bridge:1

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
