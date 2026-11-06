#!/bin/sh
# Creates the buckets of the stack in RustFS and makes some of them public.
# Run by the rustfs-init service of docker-compose.yml with the aws cli. The endpoint, the region and
# the credentials come from the AWS_* variables of that service; the names of the buckets that can be
# configured come from REQUEST_BODIES_BUCKET, RESPONSE_BODIES_BUCKET and WORDLISTS_BUCKET.
set -eu

create_bucket() {
  if aws s3api head-bucket --bucket "$1" > /dev/null 2>&1; then
    echo "bucket $1 exists"
  else
    aws s3api create-bucket --bucket "$1" > /dev/null
    echo "bucket $1 created"
  fi
}

# Same policy as the former "mc anonymous set public": anyone can list, read, write and delete.
make_public() {
  policy="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::$1\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:GetObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::$1/*\"]}]}"
  aws s3api put-bucket-policy --bucket "$1" --policy "$policy"
  echo "bucket $1 is public"
}

# All the buckets are public
for bucket in job-configuration job-results "$REQUEST_BODIES_BUCKET" "$RESPONSE_BODIES_BUCKET" "$WORDLISTS_BUCKET"; do
  create_bucket "$bucket"
  make_public "$bucket"
done
