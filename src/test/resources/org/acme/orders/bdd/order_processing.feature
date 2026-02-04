Feature: Asynchronous order processing
  Scenario: Submit an order and retrieve the result
    Given an order request for customer "cust-1" item "widget" quantity 2 unit price 10.0
    When the client submits the order
    Then the submission is accepted
    And eventually the order status is COMPLETED
    And the total price is 20.0
