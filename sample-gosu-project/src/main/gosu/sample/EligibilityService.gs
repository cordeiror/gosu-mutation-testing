package sample

/**
 * Well tested example: every mutant of this class is killed by EligibilityServiceTest.
 */
class EligibilityService {

  function isEligible(age : int, hasLicense : boolean) : boolean {
    return age >= 18 and hasLicense
  }

}
