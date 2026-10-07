package com.lucasalfare.fleconomy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FLEconomyTest {

  private val gold = Currency("GOLD")
  private val silver = Currency("SILVER")
  private val wood = ResourceRef("material", "wood")
  private val stone = ResourceRef("material", "stone")

  private fun money(amount: String, currency: Currency = gold): Money = Money.of(amount, currency)

  private fun money(amount: Long, currency: Currency = gold): Money = Money.of(amount, currency)

  private fun resource(resource: ResourceRef, amount: String): ResourceAmount = ResourceAmount.of(resource, amount)

  private fun resource(type: String, id: String, amount: String): ResourceAmount = ResourceAmount.of(type, id, amount)

  private fun economyWith(vararg ids: String): Economy {
    val economy = Economy()
    ids.forEach { economy.createAccount(AccountId(it)) }
    return economy
  }

  private fun issueMoney(economy: Economy, account: String, amount: String, currency: Currency = gold) {
    economy.issue(AccountId(account), EconomicValue.of(money(amount, currency)))
  }

  private fun issueResource(
    economy: Economy, account: String, amount: String, resource: ResourceRef = wood
  ) {
    economy.issue(AccountId(account), EconomicValue.of(resource(resource, amount)))
  }

  private fun totalMoney(economy: Economy, currency: Currency): BigDecimal =
    economy.balances().values.sumOf { it.moneyOf(currency).value }

  private fun totalResource(economy: Economy, resource: ResourceRef): BigDecimal =
    economy.balances().values.sumOf { it.resourceOf(resource).value }

  private fun assertBalance(economy: Economy, account: String, currency: Currency, amount: String) {
    assertEquals(Quantity.of(amount), economy.balanceOf(AccountId(account)).moneyOf(currency))
  }

  private fun assertResourceBalance(economy: Economy, account: String, resource: ResourceRef, amount: String) {
    assertEquals(Quantity.of(amount), economy.balanceOf(AccountId(account)).resourceOf(resource))
  }

  @Test
  fun `account id rejects blank values`() {
    assertFailsWith<IllegalArgumentException> { AccountId("") }
    assertFailsWith<IllegalArgumentException> { AccountId("   ") }
    assertFailsWith<IllegalArgumentException> { AccountId("\t") }
  }

  @Test
  fun `transaction id rejects blank values`() {
    assertFailsWith<IllegalArgumentException> { TransactionId("") }
    assertFailsWith<IllegalArgumentException> { TransactionId(" ") }
  }

  @Test
  fun `operation id rejects blank values`() {
    assertFailsWith<IllegalArgumentException> { OperationId("") }
    assertFailsWith<IllegalArgumentException> { OperationId("\n") }
  }

  @Test
  fun `loan id rejects blank values`() {
    assertFailsWith<IllegalArgumentException> { LoanId("") }
    assertFailsWith<IllegalArgumentException> { LoanId("  ") }
  }

  @Test
  fun `identifier values and equality are preserved`() {
    assertEquals("a", AccountId("a").value)
    assertEquals(AccountId("a"), AccountId("a"))
    assertEquals(TransactionId("t"), TransactionId("t"))
    assertEquals(OperationId("o"), OperationId("o"))
    assertEquals(LoanId("l"), LoanId("l"))
  }

  @Test
  fun `currency rejects blank codes and preserves equality`() {
    assertFailsWith<IllegalArgumentException> { Currency("") }
    assertFailsWith<IllegalArgumentException> { Currency(" ") }
    assertEquals(Currency("GOLD"), Currency("GOLD"))
    assertNotEquals(Currency("GOLD"), Currency("gold"))
    assertEquals("GOLD", gold.code)
  }

  @Test
  fun `quantity factory overloads create equivalent values`() {
    assertEquals(Quantity.of("10.25"), Quantity.of(BigDecimal("10.25")))
    assertEquals(Quantity.of("10"), Quantity.of(10L))
    assertEquals(Quantity.of("10"), Quantity.of("10"))
  }

  @Test
  fun `quantity preserves decimal representation in string form`() {
    assertEquals("3.4500", Quantity.of("3.4500").toString())
  }

  @Test
  fun `quantity equality comparison and hash ignore trailing zeros`() {
    val a = Quantity.of("10.00")
    val b = Quantity.of("10")

    assertEquals(a, b)
    assertEquals(0, a.compareTo(b))
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `quantity arithmetic works`() {
    assertEquals(Quantity.of("3.45"), Quantity.of("1.10") + Quantity.of("2.35"))
    assertEquals(Quantity.of("1.25"), Quantity.of("2.35") - Quantity.of("1.10"))
    assertEquals(Quantity.ZERO, Quantity.of("0.00"))
  }

  @Test
  fun `quantity rejects negative values`() {
    assertFailsWith<IllegalArgumentException> { Quantity.of("-1") }
    assertFailsWith<IllegalArgumentException> { Quantity.of(BigDecimal("-0.01")) }
    assertFailsWith<IllegalArgumentException> { Quantity.of(-1L) }
    assertFailsWith<IllegalArgumentException> { Quantity.ZERO - Quantity.of("0.01") }
  }

  @Test
  fun `quantity rejects malformed decimal strings`() {
    assertFailsWith<NumberFormatException> { Quantity.of("not-a-number") }
  }

  @Test
  fun `quantity predicates work`() {
    assertTrue(Quantity.ZERO.isZero())
    assertFalse(Quantity.ZERO.isPositive())
    assertFalse(Quantity.of("0.000").isPositive())
    assertTrue(Quantity.of("0.0001").isPositive())
  }

  @Test
  fun `money factory overloads create equivalent values`() {
    assertEquals(Money.of(BigDecimal("10.25"), gold), Money.of("10.25", gold))
    assertEquals(Money.of(10L, gold), Money.of("10", gold))
    assertEquals(Money.zero(gold), Money.of("0", gold))
  }

  @Test
  fun `money exposes quantity and currency`() {
    val value = money("12.50", silver)

    assertEquals(Quantity.of("12.50"), value.quantity)
    assertEquals(silver, value.currency)
  }

  @Test
  fun `money arithmetic requires matching currencies`() {
    val a = money("10")
    val b = money("3")

    assertEquals(money("13"), a + b)
    assertEquals(money("7"), a - b)
    assertEquals(0, a.compareTo(money("10.00")))

    assertFailsWith<IllegalArgumentException> { a + money("1", silver) }
    assertFailsWith<IllegalArgumentException> { a - money("1", silver) }
    assertFailsWith<IllegalArgumentException> { a.compareTo(money("1", silver)) }
    assertFailsWith<IllegalArgumentException> { money("2") - money("3") }
  }

  @Test
  fun `money predicates work`() {
    assertTrue(Money.zero(gold).isZero())
    assertFalse(Money.zero(gold).isPositive())
    assertTrue(money("0.01").isPositive())
  }

  @Test
  fun `resource reference validates and preserves fields`() {
    assertFailsWith<IllegalArgumentException> { ResourceRef("", "id") }
    assertFailsWith<IllegalArgumentException> { ResourceRef("type", "") }
    assertFailsWith<IllegalArgumentException> { ResourceRef(" ", "id") }
    assertFailsWith<IllegalArgumentException> { ResourceRef("type", " ") }

    assertEquals("material", wood.type)
    assertEquals("wood", wood.id)
    assertEquals(wood, ResourceRef("material", "wood"))
  }

  @Test
  fun `resource amount factory overloads work`() {
    assertEquals(ResourceAmount(wood, Quantity.of("10.25")), ResourceAmount.of(wood, BigDecimal("10.25")))
    assertEquals(ResourceAmount(wood, Quantity.of(10L)), ResourceAmount.of(wood, 10L))
    assertEquals(ResourceAmount(wood, Quantity.of("10")), ResourceAmount.of(wood, "10"))
    assertEquals(ResourceAmount(wood, Quantity.of("10")), ResourceAmount.of("material", "wood", "10"))
  }

  @Test
  fun `resource amount exposes resource and quantity`() {
    val value = ResourceAmount(wood, Quantity.of("12.5"))

    assertEquals(wood, value.resource)
    assertEquals(Quantity.of("12.5"), value.quantity)
  }

  @Test
  fun `resource amount arithmetic requires matching resources`() {
    val a = ResourceAmount(wood, Quantity.of("10"))
    val b = ResourceAmount(wood, Quantity.of("4"))
    val different = ResourceAmount(stone, Quantity.of("1"))

    assertEquals(ResourceAmount(wood, Quantity.of("14")), a + b)
    assertEquals(ResourceAmount(wood, Quantity.of("6")), a - b)
    assertEquals(0, a.compareTo(ResourceAmount(wood, Quantity.of("10.0"))))

    assertFailsWith<IllegalArgumentException> { a + different }
    assertFailsWith<IllegalArgumentException> { a - different }
    assertFailsWith<IllegalArgumentException> { a.compareTo(different) }
    assertFailsWith<IllegalArgumentException> {
      ResourceAmount(wood, Quantity.of("1")) - ResourceAmount(
        wood, Quantity.of("2")
      )
    }
  }

  @Test
  fun `resource amount predicates and zero factory work`() {
    assertTrue(ResourceAmount.zero(wood).isZero())
    assertFalse(ResourceAmount.zero(wood).isPositive())
    assertTrue(ResourceAmount(wood, Quantity.of("0.1")).isPositive())
  }

  @Test
  fun `economic value wraps money`() {
    val value = EconomicValue.of(money("5"))

    assertTrue(value is EconomicValue.Monetary)
    assertEquals(money("5"), value.money)
  }

  @Test
  fun `economic value wraps resources`() {
    val amount = ResourceAmount(wood, Quantity.of("2"))
    val value = EconomicValue.of(amount)

    assertTrue(value is EconomicValue.Resource)
    assertEquals(amount, value.amount)
  }

  @Test
  fun `balance empty singleton semantics and zero lookups work`() {
    assertSame(Balance.EMPTY, Balance.EMPTY)
    assertTrue(Balance.EMPTY.isEmpty())
    assertEquals(Quantity.ZERO, Balance.EMPTY.moneyOf(gold))
    assertEquals(Quantity.ZERO, Balance.EMPTY.resourceOf(wood))
  }

  @Test
  fun `balance reports explicit maps`() {
    val balance = Balance(
      moneys = mapOf(gold to Quantity.of("10")), resources = mapOf(wood to Quantity.of("4"))
    )

    assertEquals(mapOf(gold to Quantity.of("10")), balance.moneys)
    assertEquals(mapOf(wood to Quantity.of("4")), balance.resources)
    assertFalse(balance.isEmpty())
  }

  @Test
  fun `movement requires at least one endpoint`() {
    assertFailsWith<IllegalArgumentException> {
      Movement(null, null, EconomicValue.of(money("1")))
    }

    val issuance = Movement(null, AccountId("a"), EconomicValue.of(money("1")))
    val retirement = Movement(AccountId("a"), null, EconomicValue.of(money("1")))
    val transfer = Movement(AccountId("a"), AccountId("b"), EconomicValue.of(money("1")))

    assertNull(issuance.from)
    assertEquals(AccountId("a"), issuance.to)
    assertEquals(AccountId("a"), retirement.from)
    assertNull(retirement.to)
    assertEquals(AccountId("a"), transfer.from)
    assertEquals(AccountId("b"), transfer.to)
  }

  @Test
  fun `exchange requires at least one transfer`() {
    assertFailsWith<IllegalArgumentException> { Exchange(emptyList()) }
  }

  @Test
  fun `account creation and lookup work`() {
    val economy = Economy()
    val id = AccountId("player")

    val account = economy.createAccount(id)

    assertEquals(id, account.id)
    assertTrue(economy.accountExists(id))
    assertEquals(account, economy.getAccount(id))
    assertEquals(setOf(id), economy.accountIds())
    assertEquals(listOf(id), economy.accounts())
    assertTrue(economy.balanceOf(id).isEmpty())
    assertTrue(account.balance().isEmpty())
  }

  @Test
  fun `duplicate account creation fails without replacement`() {
    val economy = Economy()
    val id = AccountId("player")
    val original = economy.createAccount(id)

    assertFailsWith<IllegalArgumentException> { economy.createAccount(id) }

    assertEquals(original, economy.getAccount(id))
    assertEquals(1, economy.accountIds().size)
  }

  @Test
  fun `missing account lookups and balance access behave correctly`() {
    val economy = Economy()
    val missing = AccountId("missing")

    assertFalse(economy.accountExists(missing))
    assertNull(economy.getAccount(missing))
    assertFailsWith<IllegalArgumentException> { economy.balanceOf(missing) }
    assertFailsWith<IllegalArgumentException> { economy.currenciesOf(missing) }
    assertFailsWith<IllegalArgumentException> { economy.resourcesOf(missing) }
  }

  @Test
  fun `balance snapshots are detached from future mutations`() {
    val economy = economyWith("a")
    val account = AccountId("a")
    issueMoney(economy, "a", "10")
    issueResource(economy, "a", "4")

    val snapshot = economy.balanceOf(account)

    issueMoney(economy, "a", "5")
    issueResource(economy, "a", "3")

    assertEquals(Quantity.of("10"), snapshot.moneyOf(gold))
    assertEquals(Quantity.of("4"), snapshot.resourceOf(wood))
    assertEquals(Quantity.of("15"), economy.balanceOf(account).moneyOf(gold))
    assertEquals(Quantity.of("7"), economy.balanceOf(account).resourceOf(wood))
  }

  @Test
  fun `balances returns all account snapshots`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "10")
    issueResource(economy, "b", "7")

    val balances = economy.balances()

    assertEquals(2, balances.size)
    assertEquals(Quantity.of("10"), balances[AccountId("a")]!!.moneyOf(gold))
    assertEquals(Quantity.of("7"), balances[AccountId("b")]!!.resourceOf(wood))
  }

  @Test
  fun `account balance is always empty before any issuance`() {
    val economy = economyWith("a")

    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertTrue(economy.currenciesOf(AccountId("a")).isEmpty())
    assertTrue(economy.resourcesOf(AccountId("a")).isEmpty())
  }

  @Test
  fun `money issuance changes balance and appends ledger transaction`() {
    val economy = economyWith("a")

    val transaction = economy.issue(AccountId("a"), EconomicValue.of(money("100")))

    assertBalance(economy, "a", gold, "100")
    assertEquals(1, economy.ledgerSize())
    assertEquals(listOf(transaction), economy.ledgerHistory())
    assertEquals(transaction, economy.getTransaction(transaction.id))
    assertEquals(listOf(transaction), economy.transactionsByOperation(transaction.operationId))
    assertNull(transaction.movements.single().from)
    assertEquals(AccountId("a"), transaction.movements.single().to)
  }

  @Test
  fun `resource issuance changes resource balance and discovery`() {
    val economy = economyWith("a")

    val transaction = economy.issue(AccountId("a"), EconomicValue.of(resource(wood, "25")))

    assertNotNull(transaction)
    assertResourceBalance(economy, "a", wood, "25")
    assertEquals(setOf(wood), economy.resources())
    assertEquals(setOf(wood), economy.resourcesOf(AccountId("a")))
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `zero issuance commits but does not create a balance entry`() {
    val economy = economyWith("a")

    val transaction = economy.issue(AccountId("a"), EconomicValue.of(money("0")))

    assertEquals(1, economy.ledgerSize())
    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertTrue(transaction.movements.single().value is EconomicValue.Monetary)
  }

  @Test
  fun `zero resource issuance commits but does not create resource entry`() {
    val economy = economyWith("a")

    economy.issue(AccountId("a"), EconomicValue.of(resource(wood, "0")))

    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertTrue(economy.resources().isEmpty())
  }

  @Test
  fun `issuance to missing account fails atomically`() {
    val economy = Economy()

    assertFailsWith<IllegalArgumentException> {
      economy.issue(AccountId("missing"), EconomicValue.of(money("1")))
    }

    assertEquals(0, economy.ledgerSize())
    assertTrue(economy.accounts().isEmpty())
  }

  @Test
  fun `money retirement removes value and appends transaction`() {
    val economy = economyWith("a")
    issueMoney(economy, "a", "100")

    val transaction = economy.retire(AccountId("a"), EconomicValue.of(money("40")))

    assertBalance(economy, "a", gold, "60")
    assertEquals(2, economy.ledgerSize())
    assertEquals(AccountId("a"), transaction.movements.single().from)
    assertNull(transaction.movements.single().to)
  }

  @Test
  fun `resource retirement removes value`() {
    val economy = economyWith("a")
    issueResource(economy, "a", "10")

    economy.retire(AccountId("a"), EconomicValue.of(resource(wood, "4")))

    assertResourceBalance(economy, "a", wood, "6")
  }

  @Test
  fun `full retirement removes currency and resource discovery entries`() {
    val economy = economyWith("a")
    issueMoney(economy, "a", "10")
    issueResource(economy, "a", "10")

    economy.retire(AccountId("a"), EconomicValue.of(money("10")))
    economy.retire(AccountId("a"), EconomicValue.of(resource(wood, "10")))

    assertTrue(economy.currencies().isEmpty())
    assertTrue(economy.resources().isEmpty())
    assertTrue(economy.currenciesOf(AccountId("a")).isEmpty())
    assertTrue(economy.resourcesOf(AccountId("a")).isEmpty())
    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
  }

  @Test
  fun `zero retirement is accepted and leaves balance unchanged`() {
    val economy = economyWith("a")

    val transaction = economy.retire(AccountId("a"), EconomicValue.of(money("0")))

    assertEquals(1, economy.ledgerSize())
    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertEquals(AccountId("a"), transaction.movements.single().from)
    assertNull(transaction.movements.single().to)
  }

  @Test
  fun `retirement of insufficient money fails without mutation`() {
    val economy = economyWith("a")
    issueMoney(economy, "a", "10")
    val beforeBalance = economy.balanceOf(AccountId("a"))
    val beforeLedger = economy.ledgerSize()

    assertFailsWith<IllegalArgumentException> {
      economy.retire(AccountId("a"), EconomicValue.of(money("11")))
    }

    assertEquals(beforeBalance, economy.balanceOf(AccountId("a")))
    assertEquals(beforeLedger, economy.ledgerSize())
  }

  @Test
  fun `retirement of insufficient resource fails without mutation`() {
    val economy = economyWith("a")
    issueResource(economy, "a", "5")
    val before = economy.balanceOf(AccountId("a"))

    assertFailsWith<IllegalArgumentException> {
      economy.retire(AccountId("a"), EconomicValue.of(resource(wood, "6")))
    }

    assertEquals(before, economy.balanceOf(AccountId("a")))
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `money transfer atomically moves value`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "100")

    val transaction = economy.transfer(
      Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("35")))
    )

    assertBalance(economy, "a", gold, "65")
    assertBalance(economy, "b", gold, "35")
    assertEquals(2, economy.ledgerSize())
    assertEquals(1, transaction.movements.size)
  }

  @Test
  fun `resource transfer atomically moves resource`() {
    val economy = economyWith("a", "b")
    issueResource(economy, "a", "20")

    economy.transfer(
      Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(resource(wood, "7")))
    )

    assertResourceBalance(economy, "a", wood, "13")
    assertResourceBalance(economy, "b", wood, "7")
  }

  @Test
  fun `transfer rejects same source and destination`() {
    val economy = economyWith("a")
    issueMoney(economy, "a", "100")
    val before = economy.balanceOf(AccountId("a"))

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(Transfer(AccountId("a"), AccountId("a"), EconomicValue.of(money("10"))))
    }

    assertEquals(before, economy.balanceOf(AccountId("a")))
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `transfer from missing account fails without mutation`() {
    val economy = economyWith("b")

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(Transfer(AccountId("missing"), AccountId("b"), EconomicValue.of(money("10"))))
    }

    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertEquals(0, economy.ledgerSize())
  }

  @Test
  fun `transfer to missing account fails without mutation`() {
    val economy = economyWith("a")
    issueMoney(economy, "a", "10")
    val before = economy.balanceOf(AccountId("a"))

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(Transfer(AccountId("a"), AccountId("missing"), EconomicValue.of(money("1"))))
    }

    assertEquals(before, economy.balanceOf(AccountId("a")))
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `zero transfer commits without changing balances`() {
    val economy = economyWith("a", "b")

    economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("0"))))

    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `transfer with multiple charges commits one transaction in order`() {
    val economy = economyWith("buyer", "seller", "tax", "fee")
    issueMoney(economy, "buyer", "100")

    val transaction = economy.transfer(
      Transfer(AccountId("buyer"), AccountId("seller"), EconomicValue.of(money("70"))), charges = listOf(
        Charge(AccountId("buyer"), AccountId("tax"), EconomicValue.of(money("10"))),
        Charge(AccountId("buyer"), AccountId("fee"), EconomicValue.of(money("5")))
      )
    )

    assertBalance(economy, "buyer", gold, "15")
    assertBalance(economy, "seller", gold, "70")
    assertBalance(economy, "tax", gold, "10")
    assertBalance(economy, "fee", gold, "5")
    assertEquals(2, economy.ledgerSize())
    assertEquals(3, transaction.movements.size)
    assertEquals(AccountId("buyer"), transaction.movements[0].from)
    assertEquals(AccountId("seller"), transaction.movements[0].to)
    assertEquals(AccountId("tax"), transaction.movements[1].to)
    assertEquals(AccountId("fee"), transaction.movements[2].to)
  }

  @Test
  fun `charge rejects same account`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "10")

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(
        Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("1"))),
        charges = listOf(Charge(AccountId("a"), AccountId("a"), EconomicValue.of(money("1"))))
      )
    }

    assertBalance(economy, "a", gold, "10")
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `insufficient transfer with charges is atomic`() {
    val economy = economyWith("a", "b", "fee")
    issueMoney(economy, "a", "50")

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(
        Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("40"))),
        charges = listOf(Charge(AccountId("a"), AccountId("fee"), EconomicValue.of(money("20"))))
      )
    }

    assertBalance(economy, "a", gold, "50")
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertTrue(economy.balanceOf(AccountId("fee")).isEmpty())
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `insufficient resource transfer is atomic`() {
    val economy = economyWith("a", "b")
    issueResource(economy, "a", "5")

    assertFailsWith<IllegalArgumentException> {
      economy.transfer(
        Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(resource(wood, "6")))
      )
    }

    assertResourceBalance(economy, "a", wood, "5")
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `exchange applies multiple dependent legs as one transaction`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "10")

    val transaction = economy.exchange(
      Exchange(
        listOf(
          Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("10"))),
          Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(money("10")))
        )
      )
    )

    assertTrue(economy.balanceOf(AccountId("a")).isEmpty())
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertBalance(economy, "c", gold, "10")
    assertEquals(2, economy.ledgerSize())
    assertEquals(2, transaction.movements.size)
  }

  @Test
  fun `exchange supports mixed independent legs`() {
    val economy = economyWith("a", "b", "c", "d")
    issueMoney(economy, "a", "100")
    issueResource(economy, "c", "20")

    economy.exchange(
      Exchange(
        listOf(
          Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("30"))),
          Transfer(AccountId("c"), AccountId("d"), EconomicValue.of(resource(wood, "7")))
        )
      )
    )

    assertBalance(economy, "a", gold, "70")
    assertBalance(economy, "b", gold, "30")
    assertResourceBalance(economy, "c", wood, "13")
    assertResourceBalance(economy, "d", wood, "7")
  }

  @Test
  fun `exchange charges participate in the same transaction`() {
    val economy = economyWith("a", "b", "tax")
    issueMoney(economy, "a", "100")

    val transaction = economy.exchange(
      Exchange(listOf(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("60"))))),
      charges = listOf(Charge(AccountId("a"), AccountId("tax"), EconomicValue.of(money("10"))))
    )

    assertEquals(2, transaction.movements.size)
    assertEquals(2, economy.ledgerSize())
    assertBalance(economy, "a", gold, "30")
    assertBalance(economy, "b", gold, "60")
    assertBalance(economy, "tax", gold, "10")
  }

  @Test
  fun `exchange validates all legs before committing`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "100")
    issueMoney(economy, "b", "5")

    val beforeA = economy.balanceOf(AccountId("a"))
    val beforeB = economy.balanceOf(AccountId("b"))
    val beforeC = economy.balanceOf(AccountId("c"))
    val beforeLedger = economy.ledgerSize()

    assertFailsWith<IllegalArgumentException> {
      economy.exchange(
        Exchange(
          listOf(
            Transfer(AccountId("a"), AccountId("c"), EconomicValue.of(money("50"))),
            Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(money("6")))
          )
        )
      )
    }

    assertEquals(beforeA, economy.balanceOf(AccountId("a")))
    assertEquals(beforeB, economy.balanceOf(AccountId("b")))
    assertEquals(beforeC, economy.balanceOf(AccountId("c")))
    assertEquals(beforeLedger, economy.ledgerSize())
  }

  @Test
  fun `exchange with insufficient aggregated funding is atomic`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "5")

    assertFailsWith<IllegalArgumentException> {
      economy.exchange(
        Exchange(
          listOf(
            Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("3"))),
            Transfer(AccountId("a"), AccountId("c"), EconomicValue.of(money("3")))
          )
        )
      )
    }

    assertBalance(economy, "a", gold, "5")
    assertTrue(economy.balanceOf(AccountId("b")).isEmpty())
    assertTrue(economy.balanceOf(AccountId("c")).isEmpty())
    assertEquals(1, economy.ledgerSize())
  }

  @Test
  fun `exchange preserves transaction movement order`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "10")

    val transaction = economy.exchange(
      Exchange(
        listOf(
          Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("2"))),
          Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(money("1"))),
          Transfer(AccountId("c"), AccountId("a"), EconomicValue.of(money("1")))
        )
      )
    )

    assertEquals(AccountId("a"), transaction.movements[0].from)
    assertEquals(AccountId("b"), transaction.movements[0].to)
    assertEquals(AccountId("b"), transaction.movements[1].from)
    assertEquals(AccountId("c"), transaction.movements[1].to)
    assertEquals(AccountId("c"), transaction.movements[2].from)
    assertEquals(AccountId("a"), transaction.movements[2].to)
  }

  @Test
  fun `ledger transaction and operation ids are unique`() {
    val economy = economyWith("a")
    val transactions = (1..200).map {
      economy.issue(AccountId("a"), EconomicValue.of(money("1")))
    }

    assertEquals(200, transactions.map { it.id }.toSet().size)
    assertEquals(200, transactions.map { it.operationId }.toSet().size)
    assertEquals(200, economy.ledgerHistory().map { it.id }.toSet().size)
  }

  @Test
  fun `transaction lookup returns null for unknown id`() {
    assertNull(Economy().getTransaction(TransactionId("unknown")))
  }

  @Test
  fun `transactions by operation returns matching transactions only`() {
    val economy = economyWith("a")
    val first = economy.issue(AccountId("a"), EconomicValue.of(money("1")))
    val second = economy.issue(AccountId("a"), EconomicValue.of(money("2")))

    assertEquals(listOf(first), economy.transactionsByOperation(first.operationId))
    assertEquals(listOf(second), economy.transactionsByOperation(second.operationId))
    assertTrue(economy.transactionsByOperation(OperationId("does-not-exist")).isEmpty())
  }

  @Test
  fun `ledger history is ordered by commit`() {
    val economy = economyWith("a")
    val first = economy.issue(AccountId("a"), EconomicValue.of(money("1")))
    val second = economy.issue(AccountId("a"), EconomicValue.of(money("2")))
    val third = economy.retire(AccountId("a"), EconomicValue.of(money("1")))

    assertEquals(listOf(first, second, third), economy.ledgerHistory())
  }

  @Test
  fun `transaction timestamps are populated`() {
    val before = Instant.now().minusSeconds(1)
    val transaction = economyWith("a").issue(AccountId("a"), EconomicValue.of(money("1")))
    val after = Instant.now().plusSeconds(1)

    assertTrue(transaction.timestamp.isAfter(before))
    assertTrue(transaction.timestamp.isBefore(after))
  }

  @Test
  fun `currency discovery reflects positive holdings only`() {
    val economy = economyWith("a", "b")

    issueMoney(economy, "a", "10", gold)
    issueMoney(economy, "b", "20", silver)

    assertEquals(setOf(gold, silver), economy.currencies())
    assertEquals(setOf(gold), economy.currenciesOf(AccountId("a")))
    assertEquals(setOf(silver), economy.currenciesOf(AccountId("b")))

    economy.retire(AccountId("a"), EconomicValue.of(money("10", gold)))
    assertEquals(setOf(silver), economy.currencies())
  }

  @Test
  fun `resource discovery reflects positive holdings only`() {
    val economy = economyWith("a", "b")

    issueResource(economy, "a", "10", wood)
    issueResource(economy, "b", "5", stone)

    assertEquals(setOf(wood, stone), economy.resources())
    assertEquals(setOf(wood), economy.resourcesOf(AccountId("a")))
    assertEquals(setOf(stone), economy.resourcesOf(AccountId("b")))

    economy.retire(AccountId("a"), EconomicValue.of(resource(wood, "10")))
    assertEquals(setOf(stone), economy.resources())
  }

  @Test
  fun `different currencies remain isolated`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "100", gold)
    issueMoney(economy, "a", "200", silver)

    economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("30", gold))))

    assertBalance(economy, "a", gold, "70")
    assertEquals(Quantity.of("200"), economy.balanceOf(AccountId("a")).moneyOf(silver))
    assertBalance(economy, "b", gold, "30")
    assertEquals(Quantity.ZERO, economy.balanceOf(AccountId("b")).moneyOf(silver))
  }

  @Test
  fun `money and resources remain independent`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "100")
    issueResource(economy, "a", "10")

    economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("25"))))
    economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(resource(wood, "4"))))

    assertBalance(economy, "a", gold, "75")
    assertResourceBalance(economy, "a", wood, "6")
    assertBalance(economy, "b", gold, "25")
    assertResourceBalance(economy, "b", wood, "4")
  }

  @Test
  fun `two economy instances are completely independent`() {
    val first = economyWith("a")
    val second = economyWith("a")

    issueMoney(first, "a", "100")
    issueMoney(second, "a", "7", silver)

    assertBalance(first, "a", gold, "100")
    assertEquals(Quantity.ZERO, first.balanceOf(AccountId("a")).moneyOf(silver))
    assertEquals(Quantity.ZERO, second.balanceOf(AccountId("a")).moneyOf(gold))
    assertEquals(Quantity.of("7"), second.balanceOf(AccountId("a")).moneyOf(silver))
    assertEquals(1, first.ledgerSize())
    assertEquals(1, second.ledgerSize())
  }

  @Test
  fun `loan creation transfers principal and records open loan`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val dueDate = Instant.parse("2030-01-01T00:00:00Z")

    val loan = economy.createLoan(
      creditor = AccountId("creditor"),
      debtor = AccountId("debtor"),
      principal = money("40"),
      interest = money("10"),
      dueDate = dueDate
    )

    assertTrue(economy.loanExists(loan.id))
    assertEquals(loan, economy.getLoan(loan.id))
    assertEquals(AccountId("creditor"), loan.creditor)
    assertEquals(AccountId("debtor"), loan.debtor)
    assertEquals(money("40"), loan.principal)
    assertEquals(money("10"), loan.interest)
    assertEquals(money("0"), loan.paid)
    assertEquals(dueDate, loan.dueDate)
    assertEquals(LoanState.OPEN, loan.state)
    assertEquals(money("50"), loan.amountDue())
    assertFalse(loan.isFullyPaid())
    assertBalance(economy, "creditor", gold, "60")
    assertBalance(economy, "debtor", gold, "40")
    assertEquals(2, economy.ledgerSize())
  }

  @Test
  fun `loan ids are unique`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "100")
    issueMoney(economy, "b", "100")

    val first = economy.createLoan(AccountId("a"), AccountId("b"), money("10"), money("1"), Instant.now())
    val second = economy.createLoan(AccountId("b"), AccountId("c"), money("10"), money("1"), Instant.now())

    assertNotEquals(first.id, second.id)
    assertEquals(2, economy.allLoans().size)
  }

  @Test
  fun `loan creation validates participants currency principal and interest`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "100")

    assertFailsWith<IllegalArgumentException> {
      economy.createLoan(AccountId("a"), AccountId("a"), money("10"), money("1"), Instant.now())
    }
    assertFailsWith<IllegalArgumentException> {
      economy.createLoan(AccountId("a"), AccountId("missing"), money("10"), money("1"), Instant.now())
    }
    assertFailsWith<IllegalArgumentException> {
      economy.createLoan(AccountId("a"), AccountId("b"), money("10", gold), money("1", silver), Instant.now())
    }
    assertFailsWith<IllegalArgumentException> {
      economy.createLoan(AccountId("a"), AccountId("b"), money("0"), money("1"), Instant.now())
    }

    assertEquals(1, economy.ledgerSize())
    assertTrue(economy.allLoans().isEmpty())
  }

  @Test
  fun `loan creation with insufficient creditor funds is atomic`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "10")
    val beforeCreditor = economy.balanceOf(AccountId("creditor"))
    val beforeDebtor = economy.balanceOf(AccountId("debtor"))
    val beforeLedger = economy.ledgerSize()

    assertFailsWith<IllegalArgumentException> {
      economy.createLoan(
        AccountId("creditor"), AccountId("debtor"), money("11"), money("1"), Instant.now()
      )
    }

    assertEquals(beforeCreditor, economy.balanceOf(AccountId("creditor")))
    assertEquals(beforeDebtor, economy.balanceOf(AccountId("debtor")))
    assertEquals(beforeLedger, economy.ledgerSize())
    assertTrue(economy.allLoans().isEmpty())
  }

  @Test
  fun `loan payment updates paid amount and balances`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("40"), money("10"), Instant.now()
    )
    issueMoney(economy, "debtor", "10")

    val updated = economy.payLoan(loan.id, money("15"))

    assertEquals(money("15"), updated.paid)
    assertEquals(LoanState.OPEN, updated.state)
    assertEquals(money("35"), updated.amountDue())
    assertBalance(economy, "debtor", gold, "35")
    assertBalance(economy, "creditor", gold, "75")
    assertEquals(4, economy.ledgerSize())
  }

  @Test
  fun `loan payment can be split across multiple payments`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("40"), money("10"), Instant.now()
    )
    issueMoney(economy, "debtor", "10")

    val afterFirst = economy.payLoan(loan.id, money("20"))
    val afterSecond = economy.payLoan(loan.id, money("20"))
    val afterThird = economy.payLoan(loan.id, money("10"))

    assertEquals(money("20"), afterFirst.paid)
    assertEquals(money("40"), afterSecond.paid)
    assertEquals(money("50"), afterThird.paid)
    assertEquals(LoanState.PAID, afterThird.state)
    assertEquals(Quantity.of("50"), afterThird.paid.quantity)
    assertEquals(6, economy.ledgerSize())
  }

  @Test
  fun `loan becomes paid exactly at total obligation`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("40"), money("10"), Instant.now()
    )
    issueMoney(economy, "debtor", "10")

    val updated = economy.payLoan(loan.id, money("50"))

    assertEquals(LoanState.PAID, updated.state)
    assertTrue(updated.isFullyPaid())
    assertEquals(money("0"), updated.amountDue())
    assertEquals(money("50"), updated.paid)
  }

  @Test
  fun `loan payment validates lifecycle currency amount and existence`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("40"), money("10"), Instant.now()
    )
    val beforeLoan = economy.getLoan(loan.id)
    val beforeDebtor = economy.balanceOf(AccountId("debtor"))
    val beforeCreditor = economy.balanceOf(AccountId("creditor"))
    val beforeLedger = economy.ledgerSize()

    assertFailsWith<IllegalArgumentException> { economy.payLoan(LoanId("missing"), money("1")) }
    assertFailsWith<IllegalArgumentException> { economy.payLoan(loan.id, money("0")) }
    assertFailsWith<IllegalArgumentException> { economy.payLoan(loan.id, money("51")) }
    assertFailsWith<IllegalArgumentException> { economy.payLoan(loan.id, money("1", silver)) }

    assertEquals(beforeLoan, economy.getLoan(loan.id))
    assertEquals(beforeDebtor, economy.balanceOf(AccountId("debtor")))
    assertEquals(beforeCreditor, economy.balanceOf(AccountId("creditor")))
    assertEquals(beforeLedger, economy.ledgerSize())
  }

  @Test
  fun `paid and defaulted loans reject further payments`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")

    val paid = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("10"), money("0"), Instant.now()
    )
    economy.payLoan(paid.id, money("10"))

    assertFailsWith<IllegalArgumentException> { economy.payLoan(paid.id, money("1")) }

    val defaulted = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("10"), money("0"), Instant.now()
    )
    economy.defaultLoan(defaulted.id)

    assertFailsWith<IllegalArgumentException> { economy.payLoan(defaulted.id, money("1")) }
  }

  @Test
  fun `defaulting a loan changes only lifecycle state`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")
    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("30"), money("5"), Instant.now()
    )
    val beforeDebtor = economy.balanceOf(AccountId("debtor"))
    val beforeCreditor = economy.balanceOf(AccountId("creditor"))
    val beforeLedger = economy.ledgerSize()

    val updated = economy.defaultLoan(loan.id)

    assertEquals(LoanState.DEFAULTED, updated.state)
    assertEquals(beforeDebtor, economy.balanceOf(AccountId("debtor")))
    assertEquals(beforeCreditor, economy.balanceOf(AccountId("creditor")))
    assertEquals(beforeLedger, economy.ledgerSize())
  }

  @Test
  fun `defaulting paid loan is rejected and unknown loan fails`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")

    assertFailsWith<IllegalArgumentException> { economy.defaultLoan(LoanId("missing")) }

    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("10"), money("0"), Instant.now()
    )
    economy.payLoan(loan.id, money("10"))

    assertFailsWith<IllegalArgumentException> { economy.defaultLoan(loan.id) }
  }

  @Test
  fun `loan queries return expected participants and states`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "100")
    issueMoney(economy, "b", "100")

    val first = economy.createLoan(AccountId("a"), AccountId("b"), money("10"), money("1"), Instant.now())
    val second = economy.createLoan(AccountId("b"), AccountId("c"), money("20"), money("2"), Instant.now())
    val defaultedSecond = economy.defaultLoan(second.id)

    assertEquals(2, economy.allLoans().size)
    assertEquals(setOf(first), economy.loansOf(AccountId("a")).toSet())
    assertEquals(setOf(first, defaultedSecond), economy.loansOf(AccountId("b")).toSet())
    assertEquals(setOf(defaultedSecond), economy.loansOf(AccountId("c")).toSet())
    assertEquals(listOf(first), economy.loansByState(LoanState.OPEN))
    assertEquals(listOf(defaultedSecond), economy.loansByState(LoanState.DEFAULTED))
    assertTrue(economy.loansByState(LoanState.PAID).isEmpty())
    assertTrue(economy.loansOf(AccountId("nobody")).isEmpty())
  }

  @Test
  fun `past due date does not automatically default a loan`() {
    val economy = economyWith("creditor", "debtor")
    issueMoney(economy, "creditor", "100")

    val loan = economy.createLoan(
      AccountId("creditor"), AccountId("debtor"), money("10"), money("1"), Instant.parse("2000-01-01T00:00:00Z")
    )

    assertEquals(LoanState.OPEN, loan.state)
  }

  @Test
  fun `simple interest follows documented formula`() {
    assertEquals(money("25"), Interest.simple(money("100"), Quantity.of("0.05"), 5))
    assertEquals(money("7.50"), Interest.simple(money("100"), Quantity.of("0.025"), 3))
  }

  @Test
  fun `simple interest returns zero for zero inputs where documented`() {
    assertEquals(money("0"), Interest.simple(money("100"), Quantity.of("0.05"), 0))
    assertEquals(money("0"), Interest.simple(money("0"), Quantity.of("0.05"), 5))
    assertEquals(money("0"), Interest.simple(money("100"), Quantity.ZERO, 5))
  }

  @Test
  fun `simple interest rejects negative periods`() {
    assertFailsWith<IllegalArgumentException> {
      Interest.simple(money("100"), Quantity.of("0.05"), -1)
    }
  }

  @Test
  fun `compound interest follows documented formula`() {
    assertEquals(money("21"), Interest.compound(money("100"), Quantity.of("0.10"), 2))
    assertEquals(money("33.10"), Interest.compound(money("100"), Quantity.of("0.10"), 3))
  }

  @Test
  fun `compound interest handles zero inputs`() {
    assertEquals(money("0"), Interest.compound(money("100"), Quantity.of("0.10"), 0))
    assertEquals(money("0"), Interest.compound(money("0"), Quantity.of("0.10"), 5))
    assertEquals(money("0"), Interest.compound(money("100"), Quantity.ZERO, 5))
  }

  @Test
  fun `compound interest rejects negative periods`() {
    assertFailsWith<IllegalArgumentException> {
      Interest.compound(money("100"), Quantity.of("0.10"), -1)
    }
  }

  @Test
  fun `interest preserves principal currency`() {
    assertEquals(silver, Interest.simple(money("100", silver), Quantity.of("0.1"), 1).currency)
    assertEquals(silver, Interest.compound(money("100", silver), Quantity.of("0.1"), 1).currency)
  }

  @Test
  fun `closed monetary economy preserves total supply through transfers`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "1000")
    issueMoney(economy, "b", "500")

    repeat(100) {
      economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("3"))))
      economy.transfer(Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(money("2"))))
      economy.transfer(Transfer(AccountId("c"), AccountId("a"), EconomicValue.of(money("1"))))
    }

    assertEquals(BigDecimal("1500"), totalMoney(economy, gold))
    assertEquals(BigDecimal("800"), economy.balanceOf(AccountId("a")).moneyOf(gold).value)
    assertEquals(BigDecimal("600"), economy.balanceOf(AccountId("b")).moneyOf(gold).value)
    assertEquals(BigDecimal("100"), economy.balanceOf(AccountId("c")).moneyOf(gold).value)
  }

  @Test
  fun `issuance increases total supply and retirement decreases it`() {
    val economy = economyWith("a", "b", "c")

    issueMoney(economy, "a", "100")
    issueMoney(economy, "b", "50")
    assertEquals(BigDecimal("150"), totalMoney(economy, gold))

    economy.exchange(
      Exchange(
        listOf(
          Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("1"))),
          Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(money("1")))
        )
      )
    )
    assertEquals(BigDecimal("150"), totalMoney(economy, gold))

    economy.retire(AccountId("b"), EconomicValue.of(money("10")))
    assertEquals(BigDecimal("140"), totalMoney(economy, gold))
  }

  @Test
  fun `resource transfers preserve total resource quantity`() {
    val economy = economyWith("a", "b", "c")
    issueResource(economy, "a", "100")

    repeat(50) {
      economy.transfer(Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(resource(wood, "2"))))
      economy.transfer(Transfer(AccountId("b"), AccountId("c"), EconomicValue.of(resource(wood, "1"))))
      economy.transfer(Transfer(AccountId("c"), AccountId("a"), EconomicValue.of(resource(wood, "1"))))
    }

    assertEquals(BigDecimal("100"), totalResource(economy, wood))
  }

  @Test
  fun `operation relationship is preserved for multi-leg exchange`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "100")

    val transaction = economy.exchange(
      Exchange(
        listOf(
          Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("10"))),
          Transfer(AccountId("a"), AccountId("c"), EconomicValue.of(money("20")))
        )
      )
    )

    val byOperation = economy.transactionsByOperation(transaction.operationId)

    assertEquals(listOf(transaction), byOperation)
    assertEquals(2, byOperation.single().movements.size)
  }

  @Test
  fun `account creation is safe under concurrent distinct ids`() {
    val economy = Economy()
    val workers = 8
    val accountsPerWorker = 250
    val executor = Executors.newFixedThreadPool(workers)
    val start = CountDownLatch(1)
    val failures = AtomicInteger(0)

    try {
      val futures = (0 until workers).map { worker ->
        executor.submit {
          try {
            start.await()
            repeat(accountsPerWorker) { index ->
              economy.createAccount(AccountId("account-$worker-$index"))
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      start.countDown()
      futures.forEach { it.get(10, TimeUnit.SECONDS) }

      assertEquals(0, failures.get())
      assertEquals(workers * accountsPerWorker, economy.accountIds().size)
      assertEquals(workers * accountsPerWorker, economy.accounts().size)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  fun `concurrent balanced exchanges remain atomic and preserve supply`() {
    val economy = economyWith("a", "b", "c")
    issueMoney(economy, "a", "10000")

    val workers = 8
    val operationsPerWorker = 500
    val executor = Executors.newFixedThreadPool(workers + 2)
    val start = CountDownLatch(1)
    val failures = AtomicInteger(0)

    try {
      val writers = (0 until workers).map {
        executor.submit {
          try {
            start.await()
            repeat(operationsPerWorker) {
              economy.exchange(
                Exchange(
                  listOf(
                    Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("1"))),
                    Transfer(AccountId("b"), AccountId("a"), EconomicValue.of(money("1")))
                  )
                )
              )
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      val readers = (0 until 2).map {
        executor.submit {
          try {
            start.await()
            repeat(5000) {
              assertEquals(BigDecimal("10000"), totalMoney(economy, gold))
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      start.countDown()
      writers.forEach { it.get(15, TimeUnit.SECONDS) }
      readers.forEach { it.get(15, TimeUnit.SECONDS) }

      assertEquals(0, failures.get())
      assertBalance(economy, "a", gold, "10000")
      assertEquals(0, economy.balanceOf(AccountId("b")).moneyOf(gold).compareTo(Quantity.ZERO))
      assertEquals(workers * operationsPerWorker + 1, economy.ledgerSize())
      assertEquals(BigDecimal("10000"), totalMoney(economy, gold))
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  fun `concurrent issuance produces exact supply and unique ids`() {
    val economy = economyWith("a")
    val workers = 8
    val operationsPerWorker = 500
    val executor = Executors.newFixedThreadPool(workers)
    val start = CountDownLatch(1)
    val failures = AtomicInteger(0)

    try {
      val futures = (0 until workers).map {
        executor.submit {
          try {
            start.await()
            repeat(operationsPerWorker) {
              economy.issue(AccountId("a"), EconomicValue.of(money("1")))
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      start.countDown()
      futures.forEach { it.get(10, TimeUnit.SECONDS) }

      val transactions = economy.ledgerHistory()
      assertEquals(0, failures.get())
      assertEquals(workers * operationsPerWorker, transactions.size)
      assertEquals(transactions.size, transactions.map { it.id }.toSet().size)
      assertEquals(transactions.size, transactions.map { it.operationId }.toSet().size)
      assertBalance(economy, "a", gold, (workers * operationsPerWorker).toString())
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  fun `concurrent readers observe consistent complete balances`() {
    val economy = economyWith("a", "b")
    issueMoney(economy, "a", "100000")

    val workers = 4
    val readers = 4
    val operationsPerWorker = 500
    val executor = Executors.newFixedThreadPool(workers + readers)
    val start = CountDownLatch(1)
    val failures = AtomicInteger(0)

    try {
      val writerFutures = (0 until workers).map {
        executor.submit {
          try {
            start.await()
            repeat(operationsPerWorker) {
              economy.exchange(
                Exchange(
                  listOf(
                    Transfer(AccountId("a"), AccountId("b"), EconomicValue.of(money("1"))),
                    Transfer(AccountId("b"), AccountId("a"), EconomicValue.of(money("1")))
                  )
                )
              )
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      val readerFutures = (0 until readers).map {
        executor.submit {
          try {
            start.await()
            repeat(5000) {
              val balances = economy.balances()
              val a = balances[AccountId("a")]!!.moneyOf(gold).value
              val b = balances[AccountId("b")]!!.moneyOf(gold).value
              assertEquals(BigDecimal("100000"), a + b)
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      start.countDown()
      writerFutures.forEach { it.get(15, TimeUnit.SECONDS) }
      readerFutures.forEach { it.get(15, TimeUnit.SECONDS) }

      assertEquals(0, failures.get())
      assertEquals(BigDecimal("100000"), totalMoney(economy, gold))
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  @Timeout(value = 25, unit = TimeUnit.SECONDS)
  fun `mixed concurrent money and resource stress preserves independent totals`() {
    val economy = economyWith("moneyA", "moneyB", "resourceA", "resourceB")
    issueMoney(economy, "moneyA", "10000", gold)
    issueMoney(economy, "moneyB", "5000", silver)
    issueResource(economy, "resourceA", "10000", wood)

    val workers = 6
    val operationsPerWorker = 300
    val executor = Executors.newFixedThreadPool(workers)
    val start = CountDownLatch(1)
    val failures = AtomicInteger(0)

    try {
      val futures = (0 until workers).map {
        executor.submit {
          try {
            start.await()
            repeat(operationsPerWorker) { index ->
              if (index % 2 == 0) {
                economy.exchange(
                  Exchange(
                    listOf(
                      Transfer(AccountId("moneyA"), AccountId("moneyB"), EconomicValue.of(money("1", gold))),
                      Transfer(AccountId("moneyB"), AccountId("moneyA"), EconomicValue.of(money("1", gold)))
                    )
                  )
                )
              } else {
                economy.exchange(
                  Exchange(
                    listOf(
                      Transfer(AccountId("resourceA"), AccountId("resourceB"), EconomicValue.of(resource(wood, "1"))),
                      Transfer(AccountId("resourceB"), AccountId("resourceA"), EconomicValue.of(resource(wood, "1")))
                    )
                  )
                )
              }
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          }
        }
      }

      start.countDown()
      futures.forEach { it.get(20, TimeUnit.SECONDS) }

      assertEquals(0, failures.get())
      assertEquals(BigDecimal("10000"), totalMoney(economy, gold))
      assertEquals(BigDecimal("5000"), totalMoney(economy, silver))
      assertEquals(BigDecimal("10000"), totalResource(economy, wood))
    } finally {
      executor.shutdownNow()
    }
  }
}
