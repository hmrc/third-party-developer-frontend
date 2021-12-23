#!/bin/bash

sm --start ASSETS_FRONTEND -r 3.11.0

sm --start MONGO DATASTREAM EMAIL HMRC_EMAIL_RENDERER MAILGUN_STUB DESKPRO_STUB HMRCDESKPRO

sm --start API_GATEWAY_STUB THIRD_PARTY_APPLICATION THIRD_PARTY_DEVELOPER API_SUBSCRIPTION_FIELDS API_DEFINITION THIRD_PARTY_DELEGATED_AUTHORITY API_PLATFORM_EVENTS API_PLATFORM_MICROSERVICE PUSH_PULL_NOTIFICATIONS_API API_PLATFORM_XML_SERVICES PUSH_PULL_NOTIFICATIONS_GATEWAY

./run_local.sh
