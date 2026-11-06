// Rules of the rate limits of a host (HostUtils.validateRateLimits in the core).

/** What is wrong with the three limits typed in a form, or null when they are acceptable. */
export function hostLimitsProblem(host: string, short: string, long: string): string | null {
  const values = [host, short, long];
  if (values.some((v) => v.trim() === "")) {
    return "The host, short and long rate limits are all required (0 closes the host to jobs).";
  }
  const [h, s, l] = values.map(Number);
  if (![h, s, l].every((n) => Number.isInteger(n) && n >= 0)) {
    return "The rate limits are whole numbers of 0 or more.";
  }
  if (h < s + l) {
    return `The host rate limit (${h}) must be at least the short plus the long one (${s + l}).`;
  }
  return null;
}
