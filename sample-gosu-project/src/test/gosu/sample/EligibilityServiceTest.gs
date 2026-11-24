package sample

uses org.junit.jupiter.api.Assertions
uses org.junit.jupiter.api.Test

class EligibilityServiceTest {

  var _service = new EligibilityService()

  @Test
  function underageIsNotEligible() {
    Assertions.assertFalse(_service.isEligible(17, true))
  }

  @Test
  function exactlyEighteenIsEligible() {
    Assertions.assertTrue(_service.isEligible(18, true))
  }

  @Test
  function olderThanEighteenIsEligible() {
    Assertions.assertTrue(_service.isEligible(40, true))
  }

  @Test
  function adultWithoutLicenseIsNotEligible() {
    Assertions.assertFalse(_service.isEligible(40, false))
  }

  @Test
  function adultWithLicenseIsEligible() {
    Assertions.assertTrue(_service.isEligible(25, true))
  }

}
