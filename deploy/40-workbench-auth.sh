#!/bin/sh
set -eu
export LC_ALL=C
: "${WORKBENCH_AUTH_USER:?Set WORKBENCH_AUTH_USER}"
: "${WORKBENCH_AUTH_PASSWORD:?Set WORKBENCH_AUTH_PASSWORD}"
case "$WORKBENCH_AUTH_USER" in
  *[!a-zA-Z0-9_.-]*|'') echo 'Invalid authentication username' >&2; exit 1 ;;
esac
case "$WORKBENCH_AUTH_PASSWORD" in
  *'
'*|*"$(printf '\r')"*) echo 'Password must not contain line breaks' >&2; exit 1 ;;
esac
# bcrypt only uses the first 72 bytes. Reject longer passwords instead of
# silently accepting a weaker credential than the operator configured.
if [ "${#WORKBENCH_AUTH_PASSWORD}" -gt 72 ]; then
    echo 'Password must not exceed 72 bytes' >&2
    exit 1
fi
# Feed the password on stdin, never interpolate it into commands or log it.
umask 077
printf '%s\n' "$WORKBENCH_AUTH_PASSWORD" | htpasswd -niB "$WORKBENCH_AUTH_USER" > /etc/nginx/workbench.htpasswd
chown root:nginx /etc/nginx/workbench.htpasswd
chmod 640 /etc/nginx/workbench.htpasswd
unset WORKBENCH_AUTH_PASSWORD
