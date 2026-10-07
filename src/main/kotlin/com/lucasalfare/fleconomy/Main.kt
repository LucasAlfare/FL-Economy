@file:Suppress("unused")

package com.lucasalfare.fleconomy

import java.math.BigDecimal
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

  operator fun plus(other: Quantity): Quantity =
    Quantity(value + other.value)

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
    fun of(amount: BigDecimal, currency: Currency): Money =
      Money(Quantity.of(amount), currency)

    fun of(amount: Long, currency: Currency): Money =
      Money(Quantity.of(amount), currency)

    fun of(amount: String, currency: Currency): Money =
      Money(Quantity.of(amount), currency)

    fun zero(currency: Currency): Money =
      Money(Quantity.ZERO, currency)
  }
}

class Economy {
  private val lock = ReentrantReadWriteLock()
  private val operationIdGenerator = AtomicLong(0)

  internal fun nextOperationId(): OperationId {
    return OperationId(operationIdGenerator.incrementAndGet().toString())
  }

  internal fun <T> read(block: () -> T): T {
    return lock.read { block() }
  }

  internal fun <T> write(block: () -> T): T {
    return lock.write { block() }
  }
}