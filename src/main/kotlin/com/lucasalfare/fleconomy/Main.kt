@file:Suppress("unused")

package com.lucasalfare.fleconomy

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Opaque identifier for an economic account.
 *
 * Accounts are pure economic points; the application assigns meaning
 * (wallet, guild treasury, NPC, etc.).
 *
 * @property value Non-blank unique string identifier.
 * @throws IllegalArgumentException if [value] is blank.
 */
@JvmInline
value class AccountId(val value: String) {
  init {
    require(value.isNotBlank()) { "AccountId cannot be blank" }
  }
}

/**
 * Opaque identifier for a committed economic transaction.
 *
 * @property value Non-blank unique string identifier.
 * @throws IllegalArgumentException if [value] is blank.
 */
@JvmInline
value class TransactionId(val value: String) {
  init {
    require(value.isNotBlank()) { "TransactionId cannot be blank" }
  }
}

/**
 * Opaque identifier for a logical economic operation.
 *
 * Multiple [Transaction]s may share the same [OperationId] when they
 * belong to the same higher-level business action.
 *
 * @property value Non-blank unique string identifier.
 * @throws IllegalArgumentException if [value] is blank.
 */
@JvmInline
value class OperationId(val value: String) {
  init {
    require(value.isNotBlank()) { "OperationId cannot be blank" }
  }
}

/**
 * Opaque identifier for a loan.
 *
 * @property value Non-blank unique string identifier.
 * @throws IllegalArgumentException if [value] is blank.
 */
@JvmInline
value class LoanId(val value: String) {
  init {
    require(value.isNotBlank()) { "LoanId cannot be blank" }
  }
}

/**
 * Non-negative arbitrary-precision quantity used for both money and resources.
 *
 * Never holds a negative value. Arithmetic operations that would produce
 * a negative result throw [IllegalArgumentException].
 *
 * Equality and hashing ignore trailing zeros of the underlying [BigDecimal].
 *
 * @property value Underlying non-negative [BigDecimal].
 */
class Quantity private constructor(val value: BigDecimal) : Comparable<Quantity> {

  init {
    require(value >= BigDecimal.ZERO) { "Quantity cannot be negative" }
  }

  /**
   * Adds two quantities.
   *
   * @param other Quantity to add.
   * @return New [Quantity] representing the sum.
   */
  operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)

  /**
   * Subtracts [other] from this quantity.
   *
   * @param other Quantity to subtract.
   * @return New [Quantity] representing the difference.
   * @throws IllegalArgumentException if the result would be negative.
   */
  operator fun minus(other: Quantity): Quantity {
    val result = value - other.value
    require(result >= BigDecimal.ZERO) { "Subtraction would produce negative quantity" }
    return Quantity(result)
  }

  override fun compareTo(other: Quantity): Int = value.compareTo(other.value)

  /** Returns `true` when this quantity equals zero. */
  fun isZero(): Boolean = value.compareTo(BigDecimal.ZERO) == 0

  /** Returns `true` when this quantity is strictly greater than zero. */
  fun isPositive(): Boolean = value > BigDecimal.ZERO

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Quantity) return false
    return value.compareTo(other.value) == 0
  }

  override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

  override fun toString(): String = value.toPlainString()

  companion object {
    /** Zero quantity. */
    val ZERO: Quantity = Quantity(BigDecimal.ZERO)

    /**
     * Creates a [Quantity] from a [BigDecimal].
     *
     * @param value Non-negative amount.
     * @throws IllegalArgumentException if [value] is negative.
     */
    fun of(value: BigDecimal): Quantity = Quantity(value)

    /**
     * Creates a [Quantity] from a [Long].
     *
     * @param value Non-negative amount.
     * @throws IllegalArgumentException if [value] is negative.
     */
    fun of(value: Long): Quantity = Quantity(BigDecimal.valueOf(value))

    /**
     * Creates a [Quantity] from a decimal string.
     *
     * @param value Non-negative decimal representation.
     * @throws NumberFormatException if [value] is not a valid decimal.
     * @throws IllegalArgumentException if the parsed value is negative.
     */
    fun of(value: String): Quantity = Quantity(BigDecimal(value))
  }
}

/**
 * Opaque currency code.
 *
 * The library treats currencies as opaque identifiers; it never performs
 * automatic conversion between different currencies.
 *
 * @property code Non-blank currency identifier (e.g. "GOLD", "USD").
 * @throws IllegalArgumentException if [code] is blank.
 */
@JvmInline
value class Currency(val code: String) {
  init {
    require(code.isNotBlank()) { "Currency code cannot be blank" }
  }
}

/**
 * Monetary amount consisting of a non-negative [Quantity] and a [Currency].
 *
 * Arithmetic and comparison are only defined between amounts of the same currency.
 *
 * @property quantity Non-negative amount.
 * @property currency Currency of the amount.
 */
data class Money(val quantity: Quantity, val currency: Currency) : Comparable<Money> {

  init {
    require(quantity.value >= BigDecimal.ZERO)
  }

  /**
   * Adds two monetary amounts of the same currency.
   *
   * @param other Amount to add.
   * @return Sum of the two amounts.
   * @throws IllegalArgumentException if currencies differ.
   */
  operator fun plus(other: Money): Money {
    require(currency == other.currency) { "Cannot add Money of different currencies: $currency and ${other.currency}" }
    return Money(quantity + other.quantity, currency)
  }

  /**
   * Subtracts [other] from this amount (same currency required).
   *
   * @param other Amount to subtract.
   * @return Difference of the two amounts.
   * @throws IllegalArgumentException if currencies differ or the result would be negative.
   */
  operator fun minus(other: Money): Money {
    require(currency == other.currency) { "Cannot subtract Money of different currencies: $currency and ${other.currency}" }
    return Money(quantity - other.quantity, currency)
  }

  /**
   * Compares two monetary amounts of the same currency.
   *
   * @param other Amount to compare with.
   * @return Negative, zero or positive integer according to natural order.
   * @throws IllegalArgumentException if currencies differ.
   */
  override fun compareTo(other: Money): Int {
    require(currency == other.currency) { "Cannot compare Money of different currencies: $currency and ${other.currency}" }
    return quantity.compareTo(other.quantity)
  }

  /** Returns `true` when the quantity is zero. */
  fun isZero(): Boolean = quantity.isZero()

  /** Returns `true` when the quantity is strictly positive. */
  fun isPositive(): Boolean = quantity.isPositive()

  companion object {
    /**
     * Creates a [Money] instance from a [BigDecimal] amount.
     *
     * @param amount Non-negative amount.
     * @param currency Currency of the amount.
     */
    fun of(amount: BigDecimal, currency: Currency): Money = Money(Quantity.of(amount), currency)

    /**
     * Creates a [Money] instance from a [Long] amount.
     *
     * @param amount Non-negative amount.
     * @param currency Currency of the amount.
     */
    fun of(amount: Long, currency: Currency): Money = Money(Quantity.of(amount), currency)

    /**
     * Creates a [Money] instance from a decimal string.
     *
     * @param amount Non-negative decimal representation.
     * @param currency Currency of the amount.
     */
    fun of(amount: String, currency: Currency): Money = Money(Quantity.of(amount), currency)

    /**
     * Returns a zero-valued [Money] for the given currency.
     *
     * @param currency Currency of the zero amount.
     */
    fun zero(currency: Currency): Money = Money(Quantity.ZERO, currency)
  }
}

/**
 * Opaque reference to a non-monetary resource.
 *
 * The library never interprets the meaning of [type] or [id]; they are
 * treated as pure identifiers supplied by the application.
 *
 * @property type Non-blank resource type (e.g. "sword", "house", "wood").
 * @property id Non-blank resource identifier within that type.
 * @throws IllegalArgumentException if either field is blank.
 */
data class ResourceRef(val type: String, val id: String) {
  init {
    require(type.isNotBlank()) { "Resource type cannot be blank" }
    require(id.isNotBlank()) { "Resource id cannot be blank" }
  }
}

/**
 * Quantity of a specific non-monetary resource.
 *
 * Arithmetic and comparison are only defined between amounts of the same resource.
 *
 * @property resource Resource being quantified.
 * @property quantity Non-negative quantity of that resource.
 */
data class ResourceAmount(val resource: ResourceRef, val quantity: Quantity) : Comparable<ResourceAmount> {

  init {
    require(quantity.value >= BigDecimal.ZERO)
  }

  /**
   * Adds two resource amounts of the same resource.
   *
   * @param other Amount to add.
   * @return Sum of the two amounts.
   * @throws IllegalArgumentException if resources differ.
   */
  operator fun plus(other: ResourceAmount): ResourceAmount {
    require(resource == other.resource) { "Cannot add ResourceAmount of different resources: $resource and ${other.resource}" }
    return ResourceAmount(resource, quantity + other.quantity)
  }

  /**
   * Subtracts [other] from this amount (same resource required).
   *
   * @param other Amount to subtract.
   * @return Difference of the two amounts.
   * @throws IllegalArgumentException if resources differ or the result would be negative.
   */
  operator fun minus(other: ResourceAmount): ResourceAmount {
    require(resource == other.resource) { "Cannot subtract ResourceAmount of different resources: $resource and ${other.resource}" }
    return ResourceAmount(resource, quantity - other.quantity)
  }

  /**
   * Compares two resource amounts of the same resource.
   *
   * @param other Amount to compare with.
   * @return Negative, zero or positive integer according to natural order.
   * @throws IllegalArgumentException if resources differ.
   */
  override fun compareTo(other: ResourceAmount): Int {
    require(resource == other.resource) { "Cannot compare ResourceAmount of different resources: $resource and ${other.resource}" }
    return quantity.compareTo(other.quantity)
  }

  /** Returns `true` when the quantity is zero. */
  fun isZero(): Boolean = quantity.isZero()

  /** Returns `true` when the quantity is strictly positive. */
  fun isPositive(): Boolean = quantity.isPositive()

  companion object {
    /**
     * Creates a [ResourceAmount] from type, id and [BigDecimal] amount.
     *
     * @param type Resource type.
     * @param id Resource identifier.
     * @param amount Non-negative quantity.
     */
    fun of(type: String, id: String, amount: BigDecimal): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    /**
     * Creates a [ResourceAmount] from type, id and [Long] amount.
     *
     * @param type Resource type.
     * @param id Resource identifier.
     * @param amount Non-negative quantity.
     */
    fun of(type: String, id: String, amount: Long): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    /**
     * Creates a [ResourceAmount] from type, id and decimal string.
     *
     * @param type Resource type.
     * @param id Resource identifier.
     * @param amount Non-negative decimal representation.
     */
    fun of(type: String, id: String, amount: String): ResourceAmount =
      ResourceAmount(ResourceRef(type, id), Quantity.of(amount))

    /**
     * Creates a [ResourceAmount] from an existing [ResourceRef] and [BigDecimal].
     *
     * @param resource Resource reference.
     * @param amount Non-negative quantity.
     */
    fun of(resource: ResourceRef, amount: BigDecimal): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    /**
     * Creates a [ResourceAmount] from an existing [ResourceRef] and [Long].
     *
     * @param resource Resource reference.
     * @param amount Non-negative quantity.
     */
    fun of(resource: ResourceRef, amount: Long): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    /**
     * Creates a [ResourceAmount] from an existing [ResourceRef] and decimal string.
     *
     * @param resource Resource reference.
     * @param amount Non-negative decimal representation.
     */
    fun of(resource: ResourceRef, amount: String): ResourceAmount = ResourceAmount(resource, Quantity.of(amount))

    /**
     * Returns a zero-valued [ResourceAmount] for the given resource.
     *
     * @param resource Resource reference.
     */
    fun zero(resource: ResourceRef): ResourceAmount = ResourceAmount(resource, Quantity.ZERO)
  }
}

/**
 * Unified economic value that can be either monetary or a resource amount.
 *
 * Used by movements, transfers, charges and issuance/retirement operations.
 */
sealed class EconomicValue {
  /**
   * Monetary economic value.
   *
   * @property money Monetary amount.
   */
  data class Monetary(val money: Money) : EconomicValue()

  /**
   * Non-monetary resource economic value.
   *
   * @property amount Resource amount.
   */
  data class Resource(val amount: ResourceAmount) : EconomicValue()

  companion object {
    /** Wraps a [Money] instance. */
    fun of(money: Money): EconomicValue = Monetary(money)

    /** Wraps a [ResourceAmount] instance. */
    fun of(amount: ResourceAmount): EconomicValue = Resource(amount)
  }
}

/**
 * Immutable snapshot of an account's economic holdings.
 *
 * Maps that are empty for a given key are treated as zero quantity.
 *
 * @property moneys Map of currency → quantity (zero quantities are omitted).
 * @property resources Map of resource → quantity (zero quantities are omitted).
 */
data class Balance(
  val moneys: Map<Currency, Quantity>, val resources: Map<ResourceRef, Quantity>
) {
  /**
   * Returns the quantity of the given currency, or [Quantity.ZERO] if absent.
   *
   * @param currency Currency to query.
   */
  fun moneyOf(currency: Currency): Quantity = moneys[currency] ?: Quantity.ZERO

  /**
   * Returns the quantity of the given resource, or [Quantity.ZERO] if absent.
   *
   * @param resource Resource to query.
   */
  fun resourceOf(resource: ResourceRef): Quantity = resources[resource] ?: Quantity.ZERO

  /** Returns `true` when the balance contains neither money nor resources. */
  fun isEmpty(): Boolean = moneys.isEmpty() && resources.isEmpty()

  companion object {
    /** Empty balance (no money, no resources). */
    val EMPTY = Balance(emptyMap(), emptyMap())
  }
}

/**
 * Mutable economic account belonging to a single [Economy] instance.
 *
 * State is encapsulated; external code obtains only immutable [Balance] snapshots.
 * All mutations occur exclusively through the atomic commit mechanism of [Economy].
 *
 * @property id Unique account identifier.
 */
class Account internal constructor(val id: AccountId) {
  private val moneys = mutableMapOf<Currency, Quantity>()
  private val resources = mutableMapOf<ResourceRef, Quantity>()

  /**
   * Returns an immutable snapshot of the current holdings.
   */
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

/**
 * Single economic movement of value from an optional source account to an optional destination account.
 *
 * - Transfer: both [from] and [to] present.
 * - Issuance (creation of value): [from] is `null`, [to] is present.
 * - Retirement (destruction of value): [from] is present, [to] is `null`.
 *
 * At least one of [from] or [to] must be non-null.
 *
 * @property from Source account, or `null` for issuance.
 * @property to Destination account, or `null` for retirement.
 * @property value Value being moved.
 * @throws IllegalArgumentException if both [from] and [to] are null.
 */
data class Movement(
  val from: AccountId?, val to: AccountId?, val value: EconomicValue
) {
  init {
    require(from != null || to != null) { "Movement must have at least one of from or to" }
  }
}

/**
 * Immutable record of a committed economic operation.
 *
 * Once written to the ledger a [Transaction] is never modified.
 *
 * @property id Unique transaction identifier.
 * @property operationId Logical operation this transaction belongs to.
 * @property movements Ordered list of individual movements that constitute the transaction.
 * @property timestamp Instant at which the transaction was committed.
 */
data class Transaction(
  val id: TransactionId, val operationId: OperationId, val movements: List<Movement>, val timestamp: Instant
)

/**
 * Append-only historical ledger of committed transactions.
 *
 * All mutations occur exclusively under the write lock of the owning [Economy].
 */
internal class Ledger {
  private val transactions = mutableListOf<Transaction>()

  internal fun append(transaction: Transaction) {
    transactions.add(transaction)
  }

  /** Returns an immutable copy of the full transaction history. */
  fun history(): List<Transaction> = transactions.toList()

  /** Returns the number of committed transactions. */
  fun size(): Int = transactions.size

  /**
   * Looks up a transaction by its identifier.
   *
   * @param id Transaction identifier.
   * @return The transaction, or `null` if not found.
   */
  fun get(id: TransactionId): Transaction? = transactions.find { it.id == id }

  /**
   * Returns all transactions that belong to the given logical operation.
   *
   * @param operationId Logical operation identifier.
   */
  fun byOperation(operationId: OperationId): List<Transaction> = transactions.filter { it.operationId == operationId }
}

/**
 * Description of a single value transfer between two distinct accounts.
 *
 * Used as input to [Economy.transfer] and as building block of [Exchange].
 *
 * @property from Source account.
 * @property to Destination account (must differ from [from]).
 * @property value Value to transfer.
 */
data class Transfer(
  val from: AccountId, val to: AccountId, val value: EconomicValue
)

/**
 * Atomic multi-leg economic exchange composed of one or more [Transfer]s.
 *
 * All transfers are validated and applied together; either the whole exchange
 * succeeds or none of the balances change.
 *
 * @property transfers Ordered non-empty list of transfers that form the exchange.
 * @throws IllegalArgumentException if [transfers] is empty.
 */
data class Exchange(
  val transfers: List<Transfer>
) {
  init {
    require(transfers.isNotEmpty()) { "Exchange must contain at least one transfer" }
  }
}

/**
 * Additional charge (fee, tax, commission, etc.) attached to a transfer or exchange.
 *
 * The library does not interpret the semantic meaning of a charge; the application
 * decides whether it represents a fee, tax, penalty or any other concept.
 *
 * @property from Account that pays the charge.
 * @property to Account that receives the charge.
 * @property value Value of the charge.
 */
data class Charge(
  val from: AccountId, val to: AccountId, val value: EconomicValue
)

/**
 * Lifecycle state of a [Loan].
 */
enum class LoanState {
  /** Loan is active and can still receive payments. */
  OPEN,

  /** Loan has been fully repaid. */
  PAID,

  /** Loan has been marked as defaulted by the application. */
  DEFAULTED
}

/**
 * Financial obligation between a creditor and a debtor.
 *
 * Creation of a loan automatically transfers the principal from creditor to debtor.
 * Subsequent payments are performed via ordinary transfers and update the [paid] amount.
 *
 * @property id Unique loan identifier.
 * @property creditor Account that lent the principal.
 * @property debtor Account that received the principal and owes repayment.
 * @property principal Original amount lent.
 * @property interest Contracted interest amount (may be zero).
 * @property dueDate Instant after which the loan is considered overdue (application-defined).
 * @property paid Amount already repaid.
 * @property state Current lifecycle state.
 */
data class Loan(
  val id: LoanId,
  val creditor: AccountId,
  val debtor: AccountId,
  val principal: Money,
  val interest: Money,
  val dueDate: Instant,
  val paid: Money,
  val state: LoanState
) {
  /**
   * Remaining amount still owed (principal + interest − paid).
   */
  fun amountDue(): Money = principal + interest - paid

  /**
   * Returns `true` when the loan has been fully repaid.
   */
  fun isFullyPaid(): Boolean = paid >= principal + interest
}

/**
 * Central economic engine.
 *
 * Provides a thread-safe, in-memory economy supporting:
 * - accounts holding money and resources,
 * - atomic issuance, retirement, transfer and multi-leg exchange,
 * - optional charges attached to operations,
 * - interest calculation (pure function),
 * - loans with explicit payment and defaulting,
 * - append-only ledger and consistent snapshots.
 *
 * All mutating operations are atomic with respect to the entire [Economy] instance.
 * Concurrent readers are allowed; writers are exclusive.
 * Distinct [Economy] instances are completely independent.
 */
class Economy {
  private val lock = ReentrantReadWriteLock()
  private val operationIdGenerator = AtomicLong(0)
  private val transactionIdGenerator = AtomicLong(0)
  private val loanIdGenerator = AtomicLong(0)
  private val accounts = ConcurrentHashMap<AccountId, Account>()
  private val ledger = Ledger()
  private val loans = ConcurrentHashMap<LoanId, Loan>()

  private fun nextOperationId(): OperationId {
    return OperationId(operationIdGenerator.incrementAndGet().toString())
  }

  private fun nextTransactionId(): TransactionId {
    return TransactionId(transactionIdGenerator.incrementAndGet().toString())
  }

  private fun nextLoanId(): LoanId {
    return LoanId(loanIdGenerator.incrementAndGet().toString())
  }

  private fun <T> read(block: () -> T): T {
    return lock.read { block() }
  }

  private fun <T> write(block: () -> T): T {
    return lock.write { block() }
  }

  /**
   * Creates a new empty account.
   *
   * @param id Desired account identifier.
   * @return The newly created [Account].
   * @throws IllegalArgumentException if an account with the same [id] already exists.
   */
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

  /**
   * Returns the account with the given identifier, or `null` if it does not exist.
   *
   * @param id Account identifier.
   */
  fun getAccount(id: AccountId): Account? {
    return read { accounts[id] }
  }

  /**
   * Returns `true` if an account with the given identifier exists.
   *
   * @param id Account identifier.
   */
  fun accountExists(id: AccountId): Boolean {
    return read { accounts.containsKey(id) }
  }

  /**
   * Returns an immutable set of all existing account identifiers.
   */
  fun accountIds(): Set<AccountId> {
    return read { accounts.keys.toSet() }
  }

  /**
   * Returns a list of all existing account identifiers.
   */
  fun accounts(): List<AccountId> {
    return read { accounts.keys.toList() }
  }

  /**
   * Returns an immutable balance snapshot of the given account.
   *
   * @param id Account identifier.
   * @throws IllegalArgumentException if the account does not exist.
   */
  fun balanceOf(id: AccountId): Balance {
    return read {
      val account = accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
      account.balance()
    }
  }

  /**
   * Returns an immutable map of every account identifier to its current balance.
   */
  fun balances(): Map<AccountId, Balance> {
    return read {
      accounts.mapValues { it.value.balance() }
    }
  }

  /**
   * Returns the set of all currencies that appear in any account.
   */
  fun currencies(): Set<Currency> {
    return read {
      val result = mutableSetOf<Currency>()
      for (account in accounts.values) {
        result.addAll(account.balance().moneys.keys)
      }
      result.toSet()
    }
  }

  /**
   * Returns the set of currencies held by a specific account.
   *
   * @param id Account identifier.
   * @throws IllegalArgumentException if the account does not exist.
   */
  fun currenciesOf(id: AccountId): Set<Currency> {
    return read {
      val account = accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
      account.balance().moneys.keys.toSet()
    }
  }

  /**
   * Returns the set of all resources that appear in any account.
   */
  fun resources(): Set<ResourceRef> {
    return read {
      val result = mutableSetOf<ResourceRef>()
      for (account in accounts.values) {
        result.addAll(account.balance().resources.keys)
      }
      result.toSet()
    }
  }

  /**
   * Returns the set of resources held by a specific account.
   *
   * @param id Account identifier.
   * @throws IllegalArgumentException if the account does not exist.
   */
  fun resourcesOf(id: AccountId): Set<ResourceRef> {
    return read {
      val account = accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
      account.balance().resources.keys.toSet()
    }
  }

  /**
   * Returns an immutable copy of the complete ledger history.
   */
  fun ledgerHistory(): List<Transaction> {
    return read { ledger.history() }
  }

  /**
   * Returns the number of committed transactions.
   */
  fun ledgerSize(): Int {
    return read { ledger.size() }
  }

  /**
   * Looks up a transaction by its identifier.
   *
   * @param id Transaction identifier.
   * @return The transaction, or `null` if not found.
   */
  fun getTransaction(id: TransactionId): Transaction? {
    return read { ledger.get(id) }
  }

  /**
   * Returns all transactions that belong to the given logical operation.
   *
   * @param operationId Logical operation identifier.
   */
  fun transactionsByOperation(operationId: OperationId): List<Transaction> {
    return read { ledger.byOperation(operationId) }
  }

  /**
   * Looks up a loan by its identifier.
   *
   * @param id Loan identifier.
   * @return The loan, or `null` if not found.
   */
  fun getLoan(id: LoanId): Loan? {
    return read { loans[id] }
  }

  /**
   * Returns `true` if a loan with the given identifier exists.
   *
   * @param id Loan identifier.
   */
  fun loanExists(id: LoanId): Boolean {
    return read { loans.containsKey(id) }
  }

  /**
   * Returns all loans in which the given account participates
   * (either as creditor or as debtor).
   *
   * @param accountId Account identifier.
   */
  fun loansOf(accountId: AccountId): List<Loan> {
    return read {
      loans.values.filter { it.creditor == accountId || it.debtor == accountId }
    }
  }

  /**
   * Returns an immutable list of every loan known to this economy.
   */
  fun allLoans(): List<Loan> {
    return read { loans.values.toList() }
  }

  /**
   * Returns all loans that are currently in the given state.
   *
   * @param state Desired loan state.
   */
  fun loansByState(state: LoanState): List<Loan> {
    return read {
      loans.values.filter { it.state == state }
    }
  }

  private fun requireAccount(id: AccountId): Account {
    return accounts[id] ?: throw IllegalArgumentException("Account does not exist: ${id.value}")
  }

  /**
   * Atomically validates and applies a prepared [Transaction].
   *
   * The method:
   * 1. acquires exclusive write access,
   * 2. validates every movement and projects the resulting balances,
   * 3. aborts with an exception if any validation fails (no state change),
   * 4. applies all balance changes and appends the transaction to the ledger,
   * 5. releases the lock.
   *
   * No intermediate state is ever visible to concurrent readers.
   */
  private fun commit(transaction: Transaction) {
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

  /**
   * Explicitly creates (issues) value into an account.
   *
   * Corresponds to the movement `null → account`.
   *
   * @param to Destination account.
   * @param value Value to create (money or resource).
   * @return The committed [Transaction].
   * @throws IllegalArgumentException if the account does not exist or the quantity is negative.
   */
  fun issue(to: AccountId, value: EconomicValue): Transaction {
    return write {
      requireAccount(to)
      when (value) {
        is EconomicValue.Monetary -> {
          require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
            "Issuance quantity must be non-negative"
          }
        }

        is EconomicValue.Resource -> {
          require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
            "Issuance quantity must be non-negative"
          }
        }
      }
      val operationId = nextOperationId()
      val transactionId = nextTransactionId()
      val movement = Movement(from = null, to = to, value = value)
      val transaction = Transaction(
        id = transactionId, operationId = operationId, movements = listOf(movement), timestamp = Instant.now()
      )
      commit(transaction)
      transaction
    }
  }

  /**
   * Explicitly destroys (retires) value from an account.
   *
   * Corresponds to the movement `account → null`.
   * The account must hold at least the requested quantity.
   *
   * @param from Source account.
   * @param value Value to destroy (money or resource).
   * @return The committed [Transaction].
   * @throws IllegalArgumentException if the account does not exist, the quantity is negative,
   *         or the account holds insufficient funds/resources.
   */
  fun retire(from: AccountId, value: EconomicValue): Transaction {
    return write {
      requireAccount(from)
      when (value) {
        is EconomicValue.Monetary -> {
          require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
            "Retirement quantity must be non-negative"
          }
        }

        is EconomicValue.Resource -> {
          require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
            "Retirement quantity must be non-negative"
          }
        }
      }
      val operationId = nextOperationId()
      val transactionId = nextTransactionId()
      val movement = Movement(from = from, to = null, value = value)
      val transaction = Transaction(
        id = transactionId, operationId = operationId, movements = listOf(movement), timestamp = Instant.now()
      )
      commit(transaction)
      transaction
    }
  }

  /**
   * Atomically transfers value from one account to another, optionally accompanied by charges.
   *
   * The whole operation (main transfer + all charges) succeeds or fails as a unit.
   *
   * @param transfer Description of the main value movement.
   * @param charges Optional additional charges that form part of the same transaction.
   * @return The committed [Transaction].
   * @throws IllegalArgumentException on any validation failure (same account, missing account,
   *         negative quantity, insufficient balance, etc.).
   */
  fun transfer(transfer: Transfer, charges: List<Charge> = emptyList()): Transaction {
    return write {
      require(transfer.from != transfer.to) { "Cannot transfer to the same account" }
      requireAccount(transfer.from)
      requireAccount(transfer.to)
      when (val value = transfer.value) {
        is EconomicValue.Monetary -> {
          require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
            "Transfer quantity must be non-negative"
          }
        }

        is EconomicValue.Resource -> {
          require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
            "Transfer quantity must be non-negative"
          }
        }
      }
      for (charge in charges) {
        require(charge.from != charge.to) { "Cannot charge to the same account" }
        requireAccount(charge.from)
        requireAccount(charge.to)
        when (val value = charge.value) {
          is EconomicValue.Monetary -> {
            require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
              "Charge quantity must be non-negative"
            }
          }

          is EconomicValue.Resource -> {
            require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
              "Charge quantity must be non-negative"
            }
          }
        }
      }

      val operationId = nextOperationId()
      val transactionId = nextTransactionId()
      val movements = buildList {
        add(Movement(from = transfer.from, to = transfer.to, value = transfer.value))
        for (charge in charges) {
          add(Movement(from = charge.from, to = charge.to, value = charge.value))
        }
      }
      val transaction = Transaction(
        id = transactionId, operationId = operationId, movements = movements, timestamp = Instant.now()
      )
      commit(transaction)
      transaction
    }
  }

  /**
   * Atomically executes a multi-leg exchange, optionally accompanied by charges.
   *
   * All transfers and charges are validated and applied together.
   *
   * @param exchange Description of the multi-leg exchange.
   * @param charges Optional additional charges that form part of the same transaction.
   * @return The committed [Transaction].
   * @throws IllegalArgumentException on any validation failure.
   */
  fun exchange(exchange: Exchange, charges: List<Charge> = emptyList()): Transaction {
    return write {
      for (transfer in exchange.transfers) {
        require(transfer.from != transfer.to) { "Cannot transfer to the same account" }
        requireAccount(transfer.from)
        requireAccount(transfer.to)
        when (val value = transfer.value) {
          is EconomicValue.Monetary -> {
            require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
              "Transfer quantity must be non-negative"
            }
          }

          is EconomicValue.Resource -> {
            require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
              "Transfer quantity must be non-negative"
            }
          }
        }
      }
      for (charge in charges) {
        require(charge.from != charge.to) { "Cannot charge to the same account" }
        requireAccount(charge.from)
        requireAccount(charge.to)
        when (val value = charge.value) {
          is EconomicValue.Monetary -> {
            require(value.money.quantity.isPositive() || value.money.quantity.isZero()) {
              "Charge quantity must be non-negative"
            }
          }

          is EconomicValue.Resource -> {
            require(value.amount.quantity.isPositive() || value.amount.quantity.isZero()) {
              "Charge quantity must be non-negative"
            }
          }
        }
      }

      val operationId = nextOperationId()
      val transactionId = nextTransactionId()
      val movements = buildList {
        for (transfer in exchange.transfers) {
          add(Movement(from = transfer.from, to = transfer.to, value = transfer.value))
        }
        for (charge in charges) {
          add(Movement(from = charge.from, to = charge.to, value = charge.value))
        }
      }
      val transaction = Transaction(
        id = transactionId, operationId = operationId, movements = movements, timestamp = Instant.now()
      )
      commit(transaction)
      transaction
    }
  }

  /**
   * Creates a new loan by transferring the principal from creditor to debtor
   * and recording the resulting obligation.
   *
   * @param creditor Account that supplies the principal.
   * @param debtor Account that receives the principal.
   * @param principal Positive amount being lent (same currency as [interest]).
   * @param interest Non-negative contracted interest amount.
   * @param dueDate Application-defined due instant.
   * @return The newly created [Loan] in state [LoanState.OPEN].
   * @throws IllegalArgumentException on any validation failure.
   */
  fun createLoan(
    creditor: AccountId, debtor: AccountId, principal: Money, interest: Money, dueDate: Instant
  ): Loan {
    return write {
      require(creditor != debtor) { "Creditor and debtor cannot be the same account" }
      requireAccount(creditor)
      requireAccount(debtor)
      require(principal.currency == interest.currency) {
        "Principal and interest must use the same currency"
      }
      require(principal.isPositive()) { "Principal must be positive" }
      require(interest.quantity.value >= BigDecimal.ZERO) { "Interest must be non-negative" }

      val transfer = Transfer(
        from = creditor, to = debtor, value = EconomicValue.of(principal)
      )
      val transaction = transfer(transfer)
      val loanId = nextLoanId()
      val loan = Loan(
        id = loanId,
        creditor = creditor,
        debtor = debtor,
        principal = principal,
        interest = interest,
        dueDate = dueDate,
        paid = Money.zero(principal.currency),
        state = LoanState.OPEN
      )
      loans[loanId] = loan
      loan
    }
  }

  /**
   * Applies a payment toward an open loan.
   *
   * The payment is performed as an ordinary transfer from debtor to creditor
   * and the loan's [Loan.paid] amount is updated.  If the loan becomes fully
   * repaid its state changes to [LoanState.PAID].
   *
   * @param loanId Identifier of the loan being repaid.
   * @param amount Positive payment amount (same currency as the loan).
   * @return The updated [Loan].
   * @throws IllegalArgumentException if the loan does not exist, is not open,
   *         the currency mismatches, the amount is non-positive, or the payment
   *         exceeds the remaining amount due.
   */
  fun payLoan(loanId: LoanId, amount: Money): Loan {
    return write {
      val loan = loans[loanId] ?: throw IllegalArgumentException("Loan does not exist: ${loanId.value}")
      require(loan.state == LoanState.OPEN) { "Loan is not open: ${loan.state}" }
      require(amount.currency == loan.principal.currency) {
        "Payment currency must match loan currency"
      }
      require(amount.isPositive()) { "Payment amount must be positive" }

      val due = loan.amountDue()
      require(amount <= due) { "Payment exceeds amount due: ${amount.quantity} > ${due.quantity}" }

      val transfer = Transfer(
        from = loan.debtor, to = loan.creditor, value = EconomicValue.of(amount)
      )
      transfer(transfer)

      val newPaid = loan.paid + amount
      val newState = if (newPaid >= loan.principal + loan.interest) {
        LoanState.PAID
      } else {
        LoanState.OPEN
      }
      val updated = loan.copy(paid = newPaid, state = newState)
      loans[loanId] = updated
      updated
    }
  }

  /**
   * Marks an open loan as defaulted.
   *
   * No automatic balance adjustments are performed; the application decides
   * any subsequent economic consequences.
   *
   * @param loanId Identifier of the loan to default.
   * @return The updated [Loan] in state [LoanState.DEFAULTED].
   * @throws IllegalArgumentException if the loan does not exist or is not open.
   */
  fun defaultLoan(loanId: LoanId): Loan {
    return write {
      val loan = loans[loanId] ?: throw IllegalArgumentException("Loan does not exist: ${loanId.value}")
      require(loan.state == LoanState.OPEN) { "Loan is not open: ${loan.state}" }
      val updated = loan.copy(state = LoanState.DEFAULTED)
      loans[loanId] = updated
      updated
    }
  }
}

/**
 * Pure interest-calculation utilities.
 *
 * These functions never touch accounts, the ledger or any mutable state.
 * They simply compute a [Money] result from the supplied parameters.
 */
object Interest {
  /**
   * Calculates simple interest.
   *
   * Formula: `principal × rate × periods`
   *
   * @param principal Base amount.
   * @param rate Interest rate per period (non-negative).
   * @param periods Number of discrete periods (non-negative integer).
   * @return Interest amount expressed in the same currency as [principal].
   *         Returns zero when periods is zero, principal is zero or rate is zero.
   * @throws IllegalArgumentException if [periods] is negative.
   */
  fun simple(principal: Money, rate: Quantity, periods: Int): Money {
    require(periods >= 0) { "Periods must be non-negative" }
    if (periods == 0 || principal.isZero() || rate.isZero()) {
      return Money.zero(principal.currency)
    }
    val interestQuantity = Quantity.of(principal.quantity.value * rate.value * BigDecimal.valueOf(periods.toLong()))
    return Money(interestQuantity, principal.currency)
  }

  /**
   * Calculates compound interest.
   *
   * Formula: `principal × ((1 + rate)^periods − 1)`
   *
   * @param principal Base amount.
   * @param rate Interest rate per period (non-negative).
   * @param periods Number of discrete periods (non-negative integer).
   * @return Interest amount expressed in the same currency as [principal].
   *         Returns zero when periods is zero or principal is zero.
   * @throws IllegalArgumentException if [periods] is negative or the calculation
   *         produces a negative intermediate result.
   */
  fun compound(principal: Money, rate: Quantity, periods: Int): Money {
    require(periods >= 0) { "Periods must be non-negative" }
    if (periods == 0 || principal.isZero()) return Money.zero(principal.currency)
    if (rate.isZero()) return Money.zero(principal.currency)
    val onePlusRate = BigDecimal.ONE + rate.value
    val factor = onePlusRate.pow(periods)
    val finalAmount = principal.quantity.value * factor
    val interestValue = finalAmount - principal.quantity.value
    require(interestValue >= BigDecimal.ZERO) { "Compound interest calculation produced negative result" }
    return Money(Quantity.of(interestValue), principal.currency)
  }
}