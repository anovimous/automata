#!/bin/sh
# Creates the buckets of the stack in RustFS and makes sure all of them are private.
# Run by the rustfs-init service of docker-compose.yml with the aws cli. The endpoint, the region and
# the credentials come from the AWS_* variables of that service; the names of the buckets that can be
# configured come from REQUEST_BODIES_BUCKET, RESPONSE_BODIES_BUCKET and WORDLISTS_BUCKET.
#
# A bucket is private when it has no bucket policy: only the credentials of the account can use it.
# The core and the worker both sign their requests with those credentials.
set -eu

create_bucket() {
  if aws s3api head-bucket --bucket "$1" > /dev/null 2>&1; then
    echo "bucket $1 exists"
  else
    aws s3api create-bucket --bucket "$1" > /dev/null
    echo "bucket $1 created"
  fi
}

# Earlier versions of this script made the buckets public with a bucket policy. A volume created by one
# of them keeps that policy, so it is removed here.
make_private() {
  if aws s3api get-bucket-policy --bucket "$1" > /dev/null 2>&1; then
    aws s3api delete-bucket-policy --bucket "$1"
    echo "bucket $1 was public, the policy was removed"
  fi
  echo "bucket $1 is private"
}

for bucket in job-configuration job-results "$REQUEST_BODIES_BUCKET" "$RESPONSE_BODIES_BUCKET" "$WORDLISTS_BUCKET"; do
  create_bucket "$bucket"
  make_private "$bucket"
done
