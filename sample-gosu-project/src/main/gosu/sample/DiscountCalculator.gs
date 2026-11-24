package sample

/**
 * Example with intentionally incomplete tests. DiscountCalculatorTest executes every line of
 * discountFor() (100% line coverage of that function), yet several mutants survive because the
 * tests never check the boundaries or each condition on its own.
 */
class DiscountCalculator {

  /** Discount percentage for an order. */
  function discountFor(orderTotal : int, loyalCustomer : boolean) : int {
    if (orderTotal >= 1000 and loyalCustomer) {
      return 15
    }
    if (orderTotal > 500 or loyalCustomer) {
      return 5
    }
    return 0
  }

  /** Not tested at all: its mutants are reported as NO_COVERAGE. */
  function isBulkOrder(quantity : int) : boolean {
    return quantity > 50
  }

}
