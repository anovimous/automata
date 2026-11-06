package com.automata.job.domain.valueobject;

import com.automata.routine.common.enums.ResultContentType;

import io.awspring.cloud.s3.S3Resource;

public record JobResultFile(S3Resource resource, ResultContentType contentType) {

}
