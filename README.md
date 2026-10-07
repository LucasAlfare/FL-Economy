# FL Economy

### A small, atomic, thread-safe economic kernel for Kotlin/JVM.

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![](https://jitpack.io/v/LucasAlfare/FL-Economy.svg)](https://jitpack.io/#LucasAlfare/FL-Economy)

**FL Economy** is a lightweight in-memory economic library written in pure Kotlin.

It provides the mechanics required to represent and move economic value without imposing domain-specific rules on the
application.

> **“The library knows how value moves. The application knows what that value means.”**

The core can be used to build economies for games, simulations, applications, services, virtual worlds, or any system
that needs accounts, balances, resources, transfers, exchanges, charges, interest, loans, and an immutable transaction
history.

---

## Philosophy

FL Economy deliberately separates **economic mechanics** from **domain meaning**.

The library knows about:

* value;
* accounts;
* balances;
* movements;
* transactions;
* ledgers;
* transfers;
* exchanges;
* charges;
* interest calculations;
* loans;
* atomicity;
* consistency;
* concurrency.

The application decides what those concepts mean.

For example:

```kotlin
AccountId("guild-treasury")
```

may represent a guild treasury.

Or:

```kotlin
AccountId("npc-42")
```

may represent an NPC.

The library does not know.

Likewise:

```kotlin
ResourceRef("house", "123")
```

can represent a house, while:

```kotlin
ResourceRef("sword", "42")
```

can represent a sword.

Both are simply economic resources as far as the core is concerned.

This keeps the library reusable without forcing the application into a predefined game model, banking model, inventory
model, marketplace, ownership system, or taxation system.

---

## Why FL Economy?

FL Economy focuses on one problem:

**How can value move safely and atomically inside an application?**

It intentionally avoids turning that problem into a large framework.

There are no domain entities such as `Player`, `Shop`, `Bank`, `Guild`, `Inventory`, `Item`, or `Marketplace`.

There are no external databases.

There are no frameworks.

There are no asynchronous economic APIs.

There is no distributed locking.

There is no automatic currency conversion.

There is simply an in-memory economic engine with a small composable model.

---

## Features

### Precise monetary values

Money is represented using `BigDecimal` through the `Quantity` type.

There is no `Double` or `Float` for monetary values.

> Important: we didn't implemented any abstraction related to Database yet! All of this are ready to work on memory.

Currencies are explicit and opaque:

```kotlin
val gold = Currency("GOLD")
val usd = Currency("USD")
```

Different currencies cannot be silently mixed.

---

### Non-monetary resources

Economic value is not restricted to money.

Resources are represented by:

```kotlin
ResourceRef
ResourceAmount
```

A resource can represent anything identifiable by the application:

```kotlin
ResourceRef("wood", "basic")
ResourceRef("sword", "42")
ResourceRef("house", "123")
```

The library never interprets these identifiers.

---

### Accounts and immutable balances

An `Account` is simply an economic point.

Accounts can hold:

* multiple currencies;
* multiple resources.

Balances are exposed as immutable snapshots:

```kotlin
val balance = economy.balanceOf(accountId)

val goldAmount = balance.moneyOf(gold)
val swordAmount = balance.resourceOf(sword)
```

The application never receives a mutable reference to an account's internal maps.

---

### Atomic operations

Economic operations are committed atomically at the `Economy` level.

A failed operation does not partially modify state.

This applies to:

* issuance;
* retirement;
* transfers;
* exchanges;
* charges;
* loan payments.

A multi-account operation is treated as one consistent state transition.

---

### Multi-leg exchanges

`Exchange` allows multiple transfers to belong to a single transaction.

That makes operations such as:

```text
A → B : 100 GOLD
C → B : 50 GOLD
B → A : SWORD#123
```

a single atomic economic operation.

The exchange either succeeds as a whole or fails as a whole.

---

### Charges without tax or business rules

`Charge` allows additional economic movements to be attached to a transfer or exchange.

The library does not decide whether a charge is:

* a fee;
* a tax;
* a commission;
* a penalty;
* or something else.

The application supplies the meaning.

---

### Interest calculation

`Interest` contains pure functions for:

* simple interest;
* compound interest.

They operate only on supplied values and parameters.

They do not access:

* accounts;
* balances;
* loans;
* the ledger;
* mutable state.

---

### Loans

The library provides a minimal loan model with:

* creditor;
* debtor;
* principal;
* contracted interest;
* due date;
* amount paid;
* lifecycle state.

Supported states:

```text
OPEN
PAID
DEFAULTED
```

Loan principal creation and loan payments reuse the normal transfer mechanism.

There is deliberately no background scheduler and no automatic interest accumulation.

---

### Immutable transaction history

Every committed economic operation produces a `Transaction`.

Each transaction contains:

* a `TransactionId`;
* an `OperationId`;
* an ordered list of `Movement`;
* a commit timestamp.

The `Ledger` is append-only.

Committed transactions are never modified.

---

### Concurrency-aware economy

A single `Economy` instance is the unit of consistency.

Concurrent readers can execute under the read side of the economy-wide read/write lock, while writes are exclusive.

All account-changing economic operations use the same consistency boundary.

Different `Economy` instances represent completely independent economic states.

---

## Core model

The library can be understood as a small chain of concepts:

```text
Quantity
   │
   ├── Money
   │     └── Currency
   │
   └── ResourceAmount
         └── ResourceRef

Money / ResourceAmount
          │
          ▼
    EconomicValue
          │
          ▼
       Movement
          │
          ▼
      Transaction
          │
          ▼
        Ledger

Account
   │
   └── Balance

Economy
   ├── Accounts
   ├── Atomic commits
   ├── Ledger
   └── Loans
```

The central abstraction is `EconomicValue`:

```kotlin
sealed class EconomicValue
```

It unifies monetary and non-monetary value while keeping their underlying representations distinct.

---

# Getting started

## Creating an economy

An `Economy` represents an independent in-memory economic state.

```kotlin

val economy = Economy()
```

Creating another instance creates another completely independent economy:

```kotlin
val first = Economy()
val second = Economy()
```

Their accounts, balances, ledger entries, and loans are unrelated.

---

## Creating accounts

```kotlin
val alice = AccountId("alice")
val bob = AccountId("bob")

economy.createAccount(alice)
economy.createAccount(bob)
```

Account identifiers are opaque.

The application decides what they represent.

---

## Working with money

Create a currency:

```kotlin
val gold = Currency("GOLD")
```

Create money:

```kotlin
val amount = Money.of(100L, gold)
```

Decimal quantities are also supported:

```kotlin
val amount = Money.of("125.50", gold)
```

`Money` and `Quantity` never allow negative values.

---

## Issuing value

Value creation is explicit.

```kotlin

economy.issue(
  to = alice,
  value = EconomicValue.of(Money.of(500L, gold))
)
```

This represents:

```text
null → alice : 500 GOLD
```

The operation is recorded in the ledger.

---

## Transferring value

A normal transfer moves value from one account to another without creating or destroying it.

```kotlin
economy.transfer(
  Transfer(
    from = alice,
    to = bob,
    value = EconomicValue.of(Money.of(100L, gold))
  )
)
```

Conceptually:

```text
alice → bob : 100 GOLD
```

The transfer produces exactly one transaction.

---

## Retiring value

Value destruction is also explicit.

```kotlin
economy.retire(
  from = bob,
  value = EconomicValue.of(Money.of(25L, gold))
)
```

Conceptually:

```text
bob → null : 25 GOLD
```

Retirement fails when the account does not contain enough value.

---

# Resources

Money is only one kind of economic value.

Create a resource reference:

```kotlin
import com.lucasalfare.fleconomy.ResourceRef

val sword = ResourceRef(
  type = "sword",
  id = "42"
)
```

Create a resource amount:

```kotlin
import com.lucasalfare.fleconomy.ResourceAmount

val swordAmount = ResourceAmount.of(sword, 1L)
```

It can then participate in normal economic operations:

```kotlin
economy.issue(
  to = alice,
  value = EconomicValue.of(swordAmount)
)
```

The core does not know whether the resource represents a sword, house, material, token, collectible, or anything else.

---

# Exchanges

A `Transfer` represents one economic movement.

An `Exchange` composes multiple transfers into a single atomic operation.

```kotlin
import com.lucasalfare.fleconomy.Exchange
import com.lucasalfare.fleconomy.Transfer

val exchange = Exchange(
  transfers = listOf(
    Transfer(
      from = alice,
      to = bob,
      value = EconomicValue.of(Money.of(100L, gold))
    ),
    Transfer(
      from = bob,
      to = alice,
      value = EconomicValue.of(swordAmount)
    )
  )
)

economy.exchange(exchange)
```

The complete exchange becomes one `Transaction`.

There is no intermediate state in which only one leg has been committed.

---

# Charges

Charges can be attached to both transfers and exchanges.

```kotlin
import com.lucasalfare.fleconomy.Charge

val treasury = AccountId("treasury")
economy.createAccount(treasury)

economy.transfer(
  transfer = Transfer(
    from = alice,
    to = bob,
    value = EconomicValue.of(Money.of(100L, gold))
  ),
  charges = listOf(
    Charge(
      from = alice,
      to = treasury,
      value = EconomicValue.of(Money.of(5L, gold))
    )
  )
)
```

The transfer and charge belong to the same transaction.

The core does not classify the `Charge`.

Your application can decide that the movement represents a fee, tax, commission, penalty, service charge, or another
concept.

---

# Interest

Interest calculation is completely independent from the economic engine.

## Simple interest

```kotlin
import com.lucasalfare.fleconomy.Interest
import com.lucasalfare.fleconomy.Quantity

val interest = Interest.simple(
  principal = Money.of("1000", gold),
  rate = Quantity.of("0.05"),
  periods = 3
)
```

The calculation follows:

```text
principal × rate × periods
```

---

## Compound interest

```kotlin
val interest = Interest.compound(
  principal = Money.of("1000", gold),
  rate = Quantity.of("0.05"),
  periods = 3
)
```

The calculation follows:

```text
principal × ((1 + rate)^periods − 1)
```

The result is a `Money` value in the same currency as the principal.

Interest calculation does not modify accounts or write to the ledger.

---

# Loans

Loans build on the existing economic model rather than introducing a separate transfer system.

First, the creditor must have enough principal available.

```kotlin
val creditor = AccountId("creditor")
val debtor = AccountId("debtor")

economy.createAccount(creditor)
economy.createAccount(debtor)

economy.issue(
  to = creditor,
  value = EconomicValue.of(Money.of(10_000L, gold))
)
```

Create the loan:

```kotlin
import java.time.Instant

val loan = economy.createLoan(
  creditor = creditor,
  debtor = debtor,
  principal = Money.of(1_000L, gold),
  interest = Money.of(100L, gold),
  dueDate = Instant.now().plusSeconds(30 * 24 * 60 * 60)
)
```

Loan creation transfers the principal:

```text
creditor → debtor : 1,000 GOLD
```

The returned `Loan` is initially:

```text
OPEN
```

---

## Paying a loan

```kotlin
val updatedLoan = economy.payLoan(
  loanId = loan.id,
  amount = Money.of(500L, gold)
)
```

Payments are ordinary economic transfers under the hood.

When the complete amount due has been paid, the loan becomes:

```text
PAID
```

---

## Defaulting a loan

The library does not automatically alter balances when a loan defaults.

The application explicitly marks the loan:

```kotlin
economy.defaultLoan(loan.id)
```

The state becomes:

```text
DEFAULTED
```

Any additional consequences remain the responsibility of the application.

---

# Querying the economy

Balances are exposed as immutable snapshots.

```kotlin
val balance = economy.balanceOf(alice)

val goldBalance = balance.moneyOf(gold)
```

Resource balances can be queried in the same way:

```kotlin
val swords = balance.resourceOf(sword)
```

You can also inspect the overall economy:

```kotlin
economy.accountIds()
economy.currencies()
economy.resources()
economy.balances()
```

And query the transaction history:

```kotlin
economy.ledgerHistory()
economy.ledgerSize()
economy.getTransaction(transactionId)
economy.transactionsByOperation(operationId)
```

Loan queries are available through:

```kotlin
economy.getLoan(loanId)
economy.loanExists(loanId)
economy.loansOf(accountId)
economy.allLoans()
economy.loansByState(LoanState.OPEN)
```

---

# Transaction model

The ledger records **movements**, not domain events.

A `Movement` has three relevant pieces:

```text
from → to → value
```

The sides represent the nature of the movement:

```text
Transfer:
account → account

Issuance:
null → account

Retirement:
account → null
```

A `Transaction` groups one or more movements:

```text
Transaction
├── TransactionId
├── OperationId
├── Movement[]
└── timestamp
```

This makes a distinction between the physical economic movements and the higher-level operation that grouped them.

For example, an `Exchange` may contain several movements while still producing one committed transaction.

---

# Concurrency

Concurrency is scoped to an individual `Economy` instance.

The implementation uses an economy-wide `ReentrantReadWriteLock`.

The model is intentionally simple:

```text
Concurrent readers
        │
        ▼
   READ LOCK

       OR

        │
        ▼
   WRITE LOCK
        │
        ├── validate everything
        ├── calculate projected balances
        ├── apply all changes
        └── append transaction
```

A multi-account operation does not acquire independent account locks.

This avoids lock-ordering complexity and makes the consistency boundary explicit.

The same commit mechanism is used for economic operations instead of allowing individual operations to implement their
own synchronization strategy.

---

## Coroutine compatibility

FL Economy does not expose artificial `suspend` APIs.

Economic mutations are synchronous and non-suspending:

```kotlin
economy.transfer(...)
economy.exchange(...)
economy.issue(...)
economy.retire(...)
```

They can therefore be called normally from coroutine-based applications without introducing a second asynchronous
architecture into the economic core.

The library does not create or own:

* `CoroutineScope`;
* dispatchers;
* background schedulers;
* suspending locks;
* asynchronous I/O.

---

# Invariants

The design is centered around a small set of economic invariants.

### No negative balances

Accounts cannot hold negative monetary or resource quantities.

### Transfers do not create value

A normal transfer moves the requested amount from one account to another.

### Transfers do not destroy value

A normal transfer does not remove value from the economy.

### Issuance creates value explicitly

Creation occurs only through `issue`.

### Retirement destroys value explicitly

Destruction occurs only through `retire`.

### Failed operations leave state unchanged

Validation happens before projected balances are committed.

### Exchanges are atomic

All legs of an exchange succeed together or none are applied.

### Charges are atomic with their operation

A failed charge causes the complete operation to fail.

### The ledger reflects committed operations

Committed transactions are appended to the ledger and never mutated afterwards.

### Currencies never mix implicitly

Operations involving incompatible currencies fail explicitly.

### Internal mutable state does not form part of the public contract

Consumers observe snapshots and immutable transaction records rather than mutable internal maps.

---

# API overview

| Type             | Responsibility                                       |
|------------------|------------------------------------------------------|
| `Economy`        | Central economic engine and consistency boundary     |
| `Account`        | Economic point with encapsulated mutable state       |
| `AccountId`      | Opaque account identifier                            |
| `Quantity`       | Precise non-negative arbitrary-precision quantity    |
| `Currency`       | Opaque currency identifier                           |
| `Money`          | Monetary value                                       |
| `ResourceRef`    | Opaque non-monetary resource identifier              |
| `ResourceAmount` | Quantified resource value                            |
| `EconomicValue`  | Unified monetary/resource value                      |
| `Balance`        | Immutable account snapshot                           |
| `Movement`       | Single source-to-destination value movement          |
| `Transfer`       | Description of a single account-to-account operation |
| `Exchange`       | Atomic collection of transfers                       |
| `Charge`         | Additional movement attached to an operation         |
| `Transaction`    | Immutable committed operation record                 |
| `LoanId`         | Opaque loan identifier                               |
| `Loan`           | Financial obligation between two accounts            |
| `LoanState`      | Loan lifecycle state                                 |
| `Interest`       | Pure interest calculation utilities                  |
| `Ledger`         | Internal append-only transaction history             |

---

# Domain boundaries

FL Economy intentionally does **not** provide domain models.

For example, the following do not belong in the core:

```text
Player
Character
NPC
Guild
Shop
Bank
Wallet
Inventory
Item
Sword
House
Buyer
Seller
Owner
Marketplace
TaxPolicy
TradeRule
Ownership
Membership
```

Instead, the application maps its own concepts onto the economic primitives.

For example:

```text
Game domain                  FL Economy
────────────────────────────────────────────
Player wallet        ─────→ Account
Guild treasury       ─────→ Account
Gold                 ─────→ Money + Currency
Wood                 ─────→ ResourceAmount
Sword #42            ─────→ ResourceRef
Shop purchase        ─────→ Transfer / Exchange
Marketplace fee      ─────→ Charge
Loan                 ─────→ Loan
Interest calculation ─────→ Interest
Economic history     ─────→ Ledger
```

The application remains responsible for deciding what those operations mean.

---

# What FL Economy is not

FL Economy is not:

* a banking system;
* a payment gateway;
* a database;
* an accounting standard implementation;
* a distributed transaction manager;
* a currency conversion service;
* a marketplace;
* an inventory system;
* an ownership system;
* a taxation framework;
* a game engine.

It is an economic **kernel** that can be used to construct those higher-level systems.

---

# Dependencies

FL Economy is intentionally dependency-free.

The implementation uses Kotlin/JVM and standard JDK facilities such as:

* `BigDecimal`;
* `Instant`;
* `ConcurrentHashMap`;
* `AtomicLong`;
* `ReentrantReadWriteLock`.

No external framework is required.

No persistence layer is required.

No database is required.

---

# Installation

FL Economy is distributed through **JitPack**.

## Gradle Kotlin DSL

Add JitPack to your repositories:

```kotlin
repositories {
  mavenCentral()
  maven {
    url = uri("https://jitpack.io")
  }
}
```

Then add the dependency:

```kotlin
dependencies {
  implementation("com.github.LucasAlfare:FL-Economy:1.0.0")
}
```

Import the library normally:

```kotlin
import com.lucasalfare.fleconomy.*
```

---

# A complete example

The following example combines accounts, money, issuance, transfers, resources, exchanges, charges, interest, and loans.

```kotlin
fun main() {
  val economy = Economy()

  val alice = AccountId("alice")
  val bob = AccountId("bob")
  val treasury = AccountId("treasury")

  economy.createAccount(alice)
  economy.createAccount(bob)
  economy.createAccount(treasury)

  val gold = Currency("GOLD")
  val sword = ResourceRef("sword", "42")

  // Create value.
  economy.issue(
    to = alice,
    value = EconomicValue.of(Money.of(1_000L, gold))
  )

  // Create a resource.
  economy.issue(
    to = bob,
    value = EconomicValue.of(ResourceAmount.of(sword, 1L))
  )

  // Transfer money with a charge.
  economy.transfer(
    transfer = Transfer(
      from = alice,
      to = bob,
      value = EconomicValue.of(Money.of(250L, gold))
    ),
    charges = listOf(
      Charge(
        from = alice,
        to = treasury,
        value = EconomicValue.of(Money.of(10L, gold))
      )
    )
  )

  // Exchange money for a resource.
  economy.exchange(
    Exchange(
      transfers = listOf(
        Transfer(
          from = alice,
          to = bob,
          value = EconomicValue.of(Money.of(100L, gold))
        ),
        Transfer(
          from = bob,
          to = alice,
          value = EconomicValue.of(ResourceAmount.of(sword, 1L))
        )
      )
    )
  )

  // Pure interest calculation.
  val interest = Interest.simple(
    principal = Money.of(1_000L, gold),
    rate = Quantity.of("0.05"),
    periods = 2
  )

  // Create a loan.
  economy.createLoan(
    creditor = alice,
    debtor = bob,
    principal = Money.of(100L, gold),
    interest = interest,
    dueDate = Instant.now().plusSeconds(86_400)
  )
}
```

The important part is not the specific example.

The important part is that the application can construct larger economic systems by **composing the primitives provided
by the core**.

---

# Design principles

### Small surface area

The library intentionally avoids large abstraction hierarchies.

### Explicit value creation and destruction

Economic conservation is represented directly instead of being hidden inside higher-level operations.

### Atomicity first

Correctness across multiple accounts is more important than maximizing parallelism.

### Immutable observation

Balances, transactions, and loans are exposed as values rather than mutable implementation structures.

### Domain neutrality

The core never decides what an account, resource, charge, or operation means in the consuming application.

### In-memory by design

The first version focuses exclusively on deterministic local economic state.

### Composition over framework behavior

Applications build richer systems by combining simple economic primitives rather than extending a giant domain
framework.

---

# Project structure

The core is intentionally compact.

The main public model lives in the package:

```text
com.lucasalfare.fleconomy
```

Core concepts are represented directly as Kotlin types rather than being distributed across a large hierarchy of
infrastructure components.

---

# Compatibility

FL Economy targets the **Kotlin/JVM** ecosystem.

It is designed to integrate directly with ordinary Kotlin applications and coroutine-based applications without
requiring coroutine-specific APIs.

---

# License

FL Economy is distributed under the **MIT License**.

See the [`LICENSE`](LICENSE) file for the complete license text.

---

# Contributing

Contributions are welcome when they preserve the project's core philosophy:

> Keep the economic mechanics in the library.
> Keep the domain meaning in the application.

New functionality should favor small, composable primitives over domain-specific abstractions.

The core should remain lightweight, dependency-free, and independent from any particular game, business model, or
persistence technology.

---

# Status

FL Economy is designed as a focused economic kernel rather than a complete economic framework.

Its scope is intentionally limited to:

```text
Value
  ↓
Accounts
  ↓
Balances
  ↓
Movements
  ↓
Transactions
  ↓
Ledger

plus

Transfers
Exchanges
Charges
Interest
Loans
Atomicity
Concurrency
```

Everything above that layer belongs to the application.

---