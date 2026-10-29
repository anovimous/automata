package com.automata.host;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.automata.common.utils.ValidationResult;
import com.google.common.net.InternetDomainName;

public abstract class HostUtils {

	/**
	 * How many labels the host has below its registrable domain: 0 for
	 * {@code example.com} and {@code example.co.uk}, 1 for {@code api.example.com}.
	 * An IP address has no domain, so its level is 0. A name without an ICANN
	 * suffix counts the labels above its last two.
	 */
	public static int identifyLevel(String host) {

		if (InetAddressValidator.getInstance().isValid(host))
			return 0;

		InternetDomainName name = InternetDomainName.from(host);

		if (name.hasRegistrySuffix()) {
			if (!name.isUnderRegistrySuffix())
				return 0;
			return name.parts().size() - name.topDomainUnderRegistrySuffix().parts().size();
		}

		return Math.max(0, name.parts().size() - 2);

	}

	public static ValidationResult validateHostFormat(String host) {
		InetAddressValidator ipValidator = InetAddressValidator.getInstance();
		DomainValidator domainValidator = DomainValidator.getInstance(true); // allow TLDs
		// a name needs a dot: a single label (`localhost`) is not a host to test
		return ipValidator.isValid(host) || (host.contains(".") && domainValidator.isValid(host))
				? ValidationResult.valid()
				: ValidationResult.invalid();
	}

	public static ValidationResult validateRateLimits(int hostLimit, int longLimit, int shortLimit) {
		return hostLimit >= (longLimit + shortLimit) ? ValidationResult.valid() : ValidationResult.invalid();
	}

}
