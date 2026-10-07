@file:Suppress("unused")

package com.lucasalfare.fleconomy

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@JvmInline
value class AccountId(val value: String) {
  init {
    require(value.isNotBlank()) { "AccountId cannot be blank" }
  }
}

@JvmInline
value class TransactionId(val value: String) {
  init {
    require(value.isNotBlank()) { "TransactionId cannot be blank" }
  }
}

@JvmInline
value class OperationId(val value: String) {
  init {
    require(value.isNotBlank()) { "OperationId cannot be blank" }
  }
}

class Quantity private constructor(val value: BigDecimal) : Comparable<Quantity> {

  init {
    require(value >= BigDecimal.ZERO) { "Quantity cannot be negative" }
  }

  operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)

  operator fun minus(other: Quantity): Quantity {
    val result = value - other.value
    require(result >= BigDecimal.ZERO) { "Subtraction would produce negative quantity" }
    return Quantity(result)
  }

  override fun compareTo(other: Quantity): Int = value.compareTo(other.value)

  fun isZero(): Boolean = value.compareTo(BigDecimal.ZERO) == 0

  fun isPositive(): Boolean = value > BigDecimal.ZERO

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Quantity) return false
    return value.compareTo(other.value) == 0
  }

  override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

  override fun toString(): String = value.toPlainString()

  companion object {
    val ZERO: Quantity = Quantity(BigDecimal.ZERO)

    fun of(value: BigDecimal): Quantity = Quantity(value)

    fun of(value: Long): Quantity = Quantity(BigDecimal.valueOf(value))

    fun of(value: String): Quantity = Quantity(BigDecimal(value))
  }
}

@JvmInline
value class Currency(val code: String) {
  init {
    require(code.isNotBlank()) { "Currency code cannot be blank" }
  }
}

data class Money(val quantity: Quantity, val currency: Currency) : Comparable<Money> {

  init {
    require(quantity.value >= BigDecimal.ZERO)
  }

  operator fun plus(other: Money): Money {
    require(currency == other.currency) { "Cannot add Money of different currencies: $currency and ${other.currency}" }
    return Money(quantity + other.quantity, currency)
  }

  operator fun minus(other: Money): Money {
    require(currency == other.currency) { "Cannot subtract Money of different currencies: $currency and ${other.currency}" }
    return Money(quantity - other.quantity, currency)
  }

  override fun compareTo(other: Money): Int {
    require(currency == other.currency) { "Cannot compare Money of different currencies: $currency and ${other.currency}" }
    return quantity.compareTo(other.quantity)
  }

  fun isZero(): Boolean = quantity.isZero()

  fun isPositive(): Boolean = quantity.isPositive()

  companion object {
    fun of(amount: BigDecimal, currency: Currency): Money = Money(Quantity.of(amount), currency)

    fun of(amount: Long, currency: Currency): Money = Money(Quantity.of(amount), currency)

    fun of(amount: String, currency: Currency): Money = Money(Quantity.of(amount), currency)

    fun zero(currency: Currency): Money = Money(Quantity.ZERO, currency)
  }
}

data class ResourceRef(val type: String, val id: String) {
  init {
    require(type.isNotBlank()) { "Resource type cannot be blank" }
    require(id.isNotBlank()) { "Resource id cannot be blank" }
  }
}

data class ResourceAmount(val resource: ResourceRef, val quantity: Quantity) : Comparable<ResourceAmount> {

  init {
    require(quantity.value >= BigDecimal.ZERO)
  }

  operator fun plus(other: ResourceAmount): ResourceAmount {
    require(resource == other.resource) { "Cannot add ResourceAmount of different resources: $resource and ${other.resource}" }
    return ResourceAmount(resource, quantity + other.quantity)
  }

  operator fun minus(other: ResourceAmount): ResourceAmount {
    require(resource == other.resource) { "Cannot subtract ResourceAmount of different resources: $resource and ${other.resource}" }
    return ResourceAmount(resource, quantity - other.quantity)
  }

  override fun compareTo(other: ResourceAmount): Int {
    require(resource == other.resource) { "Cannot compare ResourceAmount of different resources: $resource and ${other.resource}" }
    return quantity.compareTo(other.quantity)
  }

  fun isZero(): Boolean = quantity.isZero()

  fun isPositive(): Boolean = quantity.isPositive()

  companion object {
    fun of(type: String, id: String, amount: BigDecimal): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    fun of(type: String, id: String, amount: Long): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    fun of(type: String, id: String, amount: String): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    fun of(resource: ResourceRef, amount: BigDecimal): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    fun of(resource: ResourceRef, amount: Long): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    fun of(resource: ResourceRef, amount: String): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    fun zero(resource: ResourceRef): ResourceAmount = ResourceAmount(resource, Quantity.ZERO)
  }
}

sealed class EconomicValue {
  data class Monetary(val money: Money) : EconomicValue()
  data class Resource(val amount: ResourceAmount) : EconomicValue()

  companion object {
    fun of(money: Money): EconomicValue = Monetary(money)
    fun of(amount: ResourceAmount): EconomicValue = Resource(amount)
  }
}

data class Balance(
  val moneys: Map<Currency, Quantity>, val resources: Map<ResourceRef, Quantity>
) {
  fun moneyOf(currency: Currency): Quantity = moneys[currency] ?: Quantity.ZERO

  fun resourceOf(resource: ResourceRef): Quantity = resources[resource] ?: Quantity.ZERO

  fun isEmpty(): Boolean = moneys.isEmpty() && resources.isEmpty()

  companion object {
    val EMPTY = Balance(emptyMap(), emptyMap())
  }
}

class Account internal constructor(val id: AccountId) {
  private val moneys = mutableMapOf<Currency, Quantity>()
  private val resources = mutableMapOf<ResourceRef, Quantity>()

  fun balance(): Balance {
    return Balance(
      moneys = moneys.toMap(), resources = resources.toMap()
    )
  }

  internal fun getMoney(currency: Currency): Quantity = moneys[currency] ?: Quantity.ZERO

  internal fun getResource(resource: ResourceRef): Quantity = resources[resource] ?: Quantity.ZERO

  internal fun setMoney(currency: Currency, quantity: Quantity) {
    if (quantity.isZero()) {
      moneys.remove(currency)
    } else {
      moneys[currency] = quantity
    }
  }

  internal fun setResource(resource: ResourceRef, quantity: Quantity) {
    if (quantity.isZero()) {
      resources.remove(resource)
    } else {
      resources[resource] = quantity
    }
  }

  internal fun addMoney(money: Money) {
    val current = getMoney(money.currency)
    setMoney(money.currency, current + money.quantity)
  }

  internal fun subtractMoney(money: Money) {
    val current = getMoney(money.currency)
    setMoney(money.currency, current - money.quantity)
  }

  internal fun addResource(amount: ResourceAmount) {
    val current = getResource(amount.resource)
    setResource(amount.resource, current + amount.quantity)
  }

  internal fun subtractResource(amount: ResourceAmount) {
    val current = getResource(amount.resource)
    setResource(amount.resource, current - amount.quantity)
  }

  internal fun apply(value: EconomicValue, add: Boolean) {
    when (value) {
      is EconomicValue.Monetary -> {
        if (add) addMoney(value.money) else subtractMoney(value.money)
      }

      is EconomicValue.Resource -> {
        if (add) addResource(value.amount) else subtractResource(value.amount)
      }
    }
  }
}

data class Movement(
  val from: AccountId?, val to: AccountId?, val value: EconomicValue
) {
  init {
    require(from != null || to != null) { "Movement must have at least one of from or to" }
  }
}

data class Transaction(
  val id: TransactionId, val operationId: OperationId, val movements: List<Movement>, val timestamp: Instant
)

class Ledger {
  private val transactions = mutableListOf<Transaction>()

  internal fun append(transaction: Transaction) {
    transactions.add(transaction)
  }

  fun history(): List<Transaction> = transactions.toList()

  fun size(): Int = transactions.size
}

class Economy {
  private val lock = ReentrantReadWriteLock()
  private val operationIdGenerator = AtomicLong(0)
  private val transactionIdGenerator = AtomicLong(0)
  private val accounts = ConcurrentHashMap<AccountId, Account>()
  private val ledger = Ledger()

  internal fun nextOperationId(): OperationId {
    return OperationId(operationIdGenerator.incrementAndGet().toString())
  }

  internal fun nextTransactionId(): TransactionId {
    return TransactionId(transactionIdGenerator.incrementAndGet().toString())
  }

  internal fun <T> read(block: () -> T): T {
    return lock.read { block() }
  }

  internal fun <T> write(block: () -> T): T {
    return lock.write { block() }
  }

  fun createAccount(id: AccountId): Account {
    return write {
      if (accounts.containsKey(id)) {
        throw IllegalArgumentException("Account already exists: ${id.value}")
      }
      val account = Account(id)
      accounts[id] = account
      account
    }
  }

  fun getAccount(id: AccountId): Account? {
    return read { accounts[id] }
  }

  fun accountExists(id: AccountId): Boolean {
    return read { accounts.containsKey(id) }
  }

  fun balanceOf(id: AccountId): Balance {
    return read {
      val account = accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
      account.balance()
    }
  }

  fun ledgerHistory(): List<Transaction> {
    return read { ledger.history() }
  }

  internal fun requireAccount(id: AccountId): Account {
    return accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
  }

  internal fun getLedger(): Ledger = ledger

  internal fun commit(transaction: Transaction) {
    write {
      val pendingDeltas = mutableMapOf<AccountId, MutableList<Pair<EconomicValue, Boolean>>>()

      for (movement in transaction.movements) {
        val value = movement.value
        when (value) {
          is EconomicValue.Monetary -> {
            require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
              "Economic value quantity must be non-negative"
            }
          }

          is EconomicValue.Resource -> {
            require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
              "Economic value quantity must be non-negative"
            }
          }
        }

        if (movement.from != null) {
          requireAccount(movement.from)
          pendingDeltas.getOrPut(movement.from) { mutableListOf() }.add(value to false)
        }
        if (movement.to != null) {
          requireAccount(movement.to)
          pendingDeltas.getOrPut(movement.to) { mutableListOf() }.add(value to true)
        }
      }

      val projected = mutableMapOf<AccountId, Balance>()
      for ((accountId, deltas) in pendingDeltas) {
        val account = requireAccount(accountId)
        val current = account.balance()
        val moneys = current.moneys.toMutableMap()
        val resources = current.resources.toMutableMap()

        for ((value, add) in deltas) {
          when (value) {
            is EconomicValue.Monetary -> {
              val currency = value.money.currency
              val currentQty = moneys[currency] ?: Quantity.ZERO
              val newQty = if (add) {
                currentQty + value.money.quantity
              } else {
                require(currentQty >= value.money.quantity) {
                  "Insufficient balance for ${currency.code} in account ${accountId.value}"
                }
                currentQty - value.money.quantity
              }
              if (newQty.isZero()) {
                moneys.remove(currency)
              } else {
                moneys[currency] = newQty
              }
            }

            is EconomicValue.Resource -> {
              val resource = value.amount.resource
              val currentQty = resources[resource] ?: Quantity.ZERO
              val newQty = if (add) {
                currentQty + value.amount.quantity
              } else {
                require(currentQty >= value.amount.quantity) {
                  "Insufficient resource ${resource.type}:${resource.id} in account ${accountId.value}"
                }
                currentQty - value.amount.quantity
              }
              if (newQty.isZero()) {
                resources.remove(resource)
              } else {
                resources[resource] = newQty
              }
            }
          }
        }
        projected[accountId] = Balance(moneys.toMap(), resources.toMap())
      }

      for ((accountId, newBalance) in projected) {
        val account = requireAccount(accountId)
        for ((currency, _) in account.balance().moneys) {
          if (currency !in newBalance.moneys) {
            account.setMoney(currency, Quantity.ZERO)
          }
        }
        for ((resource, _) in account.balance().resources) {
          if (resource !in newBalance.resources) {
            account.setResource(resource, Quantity.ZERO)
          }
        }
        for ((currency, qty) in newBalance.moneys) {
          account.setMoney(currency, qty)
        }
        for ((resource, qty) in newBalance.resources) {
          account.setResource(resource, qty)
        }
      }

      ledger.append(transaction)
    }
  }
}