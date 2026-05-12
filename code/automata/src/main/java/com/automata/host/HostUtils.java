package com.automata.host;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.automata.common.utils.ValidationResult;

public abstract class HostUtils {

	public static int identifyLevel(String host) {
		return host.split("\\.").length - 2;

	}

	public static ValidationResult validateHostFormat(String host) {
		InetAddressValidator ipValidator = InetAddressValidator.getInstance();
		DomainValidator domainValidator = DomainValidator.getInstance(true); // allow TLDs
		return ipValidator.isValid(host) || domainValidator.isValid(host) ? ValidationResult.valid()
				: ValidationResult.invalid();
	}

	public static ValidationResult validateRateLimits(int hostLimit, int longLimit, int shortLimit) {
		return hostLimit >= (longLimit + shortLimit) ? ValidationResult.valid() : ValidationResult.invalid();
	}

}
