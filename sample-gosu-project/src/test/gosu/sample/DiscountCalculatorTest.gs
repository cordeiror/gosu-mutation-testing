package sample

uses org.junit.jupiter.api.Assertions
uses org.junit.jupiter.api.Test

/**
 * Intentionally incomplete: these three tests reach every line of discountFor(), but they use
 * values far from the boundaries and never isolate each condition.
 */
class DiscountCalculatorTest {

  var _calculator = new DiscountCalculator()

  @Test
  function bigOrderFromLoyalCustomerGetsFifteenPercent() {
    Assertions.assertEquals(15, _calculator.discountFor(2000, true))
  }

  @Test
  function loyalCustomerGetsFivePercent() {
    Assertions.assertEquals(5, _calculator.discountFor(700, true))
  }

  @Test
  function smallOrderFromNewCustomerGetsNothing() {
    Assertions.assertEquals(0, _calculator.discountFor(100, false))
  }

}
