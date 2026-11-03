#!/bin/sh
set -eu
umask 077

: "${DB_CA_CERT_B64:?Set the base64-encoded Aiven CA certificate in the hosting environment.}"
: "${DB_TRUSTSTORE_PASSWORD:?Set the truststore password in the hosting environment.}"

CERT_DIR="$(mktemp -d /tmp/etm-certs.XXXXXX)"
printf '%s' "$DB_CA_CERT_B64" | base64 --decode > "$CERT_DIR/aiven-ca.pem"
keytool -importcert -noprompt -alias aiven-ca \
  -file "$CERT_DIR/aiven-ca.pem" \
  -keystore "$CERT_DIR/aiven-truststore.p12" \
  -storetype PKCS12 -storepass:env DB_TRUSTSTORE_PASSWORD

export DB_TRUSTSTORE_URL="file://$CERT_DIR/aiven-truststore.p12"
unset DB_CA_CERT_B64
exec java -jar /app/app.jar
