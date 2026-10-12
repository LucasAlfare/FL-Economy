# Sumário da API Pública

## Visão Geral

Biblioteca Kotlin para modelagem econômica thread-safe e em memória (`fleconomy`). Suporta contas que guardam dinheiro e
recursos, operações atômicas (emissão, aposentadoria, transferência, troca multi-perna), taxas/cargos opcionais,
empréstimos com pagamento e default, ledger append-only, snapshots consistentes e observação síncrona de eventos. Todas
as mutações são atômicas em relação à instância de `Economy`; leitores concorrentes são permitidos, escritores são
exclusivos.

## Métodos e Tipos Públicos

### Identificadores opacos (value classes)

- `AccountId(value: String)` — identificador de conta (não pode ser blank)
- `TransactionId(value: String)` — identificador de transação commitada
- `OperationId(value: String)` — identificador de operação lógica de alto nível
- `LoanId(value: String)` — identificador de empréstimo

---

### `Quantity`

**Propósito:**  
Quantidade não-negativa de precisão arbitraria (usada tanto para dinheiro quanto para recursos). Nunca armazena valor
negativo.

**Principais membros:**

- `plus(other: Quantity) → Quantity`
- `minus(other: Quantity) → Quantity` (lança se o resultado for negativo)
- `compareTo(other: Quantity) → Int`
- `isZero() → Boolean`
- `isPositive() → Boolean`

**Factory (companion):**

- `Quantity.ZERO`
- `Quantity.of(value: BigDecimal)`
- `Quantity.of(value: Long)`
- `Quantity.of(value: String)`

---

### `Currency(code: String)`

**Propósito:**  
Código de moeda opaco. A biblioteca nunca converte automaticamente entre moedas.

---

### `Money(quantity: Quantity, currency: Currency)`

**Propósito:**  
Valor monetário (quantidade + moeda). Operações aritméticas e comparação só são permitidas entre a mesma moeda.

**Principais membros:**

- `plus` / `minus` / `compareTo`
- `isZero()` / `isPositive()`

**Factory:**

- `Money.of(amount: BigDecimal|Long|String, currency: Currency)`
- `Money.zero(currency: Currency)`

---

### `ResourceRef(type: String, id: String)`

**Propósito:**  
Referência opaca a um recurso não-monetário (ex.: `"sword"`, `"wood"`).

---

### `ResourceAmount(resource: ResourceRef, quantity: Quantity)`

**Propósito:**  
Quantidade de um recurso específico. Aritmética e comparação só entre o mesmo recurso.

**Factory:** várias sobrecargas `of(...)` e `zero(resource)`.

---

### `EconomicValue` (sealed)

**Propósito:**  
Valor econômico unificado (dinheiro ou recurso).

Subtipos:

- `Monetary(money: Money)`
- `Resource(amount: ResourceAmount)`

Factory: `EconomicValue.of(money)` / `EconomicValue.of(amount)`.

---

### `Balance(moneys: Map<Currency, Quantity>, resources: Map<ResourceRef, Quantity>)`

**Propósito:**  
Snapshot imutável das holdings de uma conta.

Métodos:

- `moneyOf(currency) → Quantity`
- `resourceOf(resource) → Quantity`
- `isEmpty() → Boolean`

Constante: `Balance.EMPTY`.

---

### `Account` (criado apenas via `Economy`)

**Propósito:**  
Conta econômica mutável pertencente a uma única `Economy`. Estado encapsulado; código externo só obtém snapshots
imutáveis via `balance()`.

Método público:

- `balance() → Balance`

---

### `Movement(from: AccountId?, to: AccountId?, value: EconomicValue)`

**Propósito:**  
Movimento único de valor.

- Transferência: ambos `from` e `to` presentes
- Emissão: `from == null`
- Aposentadoria: `to == null`

---

### `Transaction(id, operationId, movements, timestamp)`

**Propósito:**  
Registro imutável de uma operação econômica commitada no ledger.

---

### `BalanceChange(accountId, previousBalance, newBalance)`

**Propósito:**  
Descrição imutável da mudança de saldo de uma conta após uma transação.

---

### `EconomyEvent` (sealed interface)

**Propósito:**  
Fatos imutáveis emitidos após mudanças de estado bem-sucedidas.

Subtipos:

- `AccountCreated(accountId)`
- `TransactionCommitted(transaction, balanceChanges)`
- `LoanCreated(loan, transaction)`
- `LoanPaid(previousLoan, loan, transaction)`
- `LoanDefaulted(previousLoan, loan)`

---

### `EconomyEventListener` (fun interface)

**Propósito:**  
Callback de observação de eventos.

Método: `onEvent(event: EconomyEvent)`

---

### `EconomySubscription`

**Propósito:**  
Handle de inscrição de eventos.

Métodos:

- `unsubscribe()` (idempotente)
- `isActive() → Boolean`

---

### `Transfer(from, to, value)`

**Propósito:**  
Descrição de uma transferência entre duas contas distintas.

---

### `Exchange(transfers: List<Transfer>)`

**Propósito:**  
Troca multi-perna atômica (todas as pernas validam e aplicam juntas).

---

### `Charge(from, to, value)`

**Propósito:**  
Cargo adicional (taxa, imposto, comissão etc.) anexado a uma transferência ou troca.

---

### `LoanState` (enum)

Valores: `OPEN`, `PAID`, `DEFAULTED`.

---

### `Loan(...)`

**Propósito:**  
Obrigação financeira entre credor e devedor.

Propriedades: `id`, `creditor`, `debtor`, `principal`, `interest`, `dueDate`, `paid`, `state`.

Métodos:

- `amountDue() → Money`
- `isFullyPaid() → Boolean`

---

### `Economy`

**Propósito:**  
Motor econômico central, thread-safe e em memória.

#### Observação de eventos

| Método         | Assinatura                                               | Propósito                               |
|----------------|----------------------------------------------------------|-----------------------------------------|
| `subscribe`    | `(listener: EconomyEventListener) → EconomySubscription` | Inscreve em todos os eventos            |
| `subscribe<E>` | `(listener: (E) → Unit) → EconomySubscription`           | Inscreve apenas em um subtipo de evento |

#### Contas

| Método          | Assinatura                           | Propósito                   |
|-----------------|--------------------------------------|-----------------------------|
| `createAccount` | `(id: AccountId) → Account`          | Cria conta vazia            |
| `getAccount`    | `(id: AccountId) → Account?`         | Obtém conta                 |
| `accountExists` | `(id: AccountId) → Boolean`          | Verifica existência         |
| `accountIds`    | `() → Set<AccountId>`                | Todos os ids de conta       |
| `accounts`      | `() → List<AccountId>`               | Lista de ids de conta       |
| `balanceOf`     | `(id: AccountId) → Balance`          | Snapshot de saldo           |
| `balances`      | `() → Map<AccountId, Balance>`       | Todos os saldos             |
| `currencies`    | `() → Set<Currency>`                 | Todas as moedas presentes   |
| `currenciesOf`  | `(id: AccountId) → Set<Currency>`    | Moedas de uma conta         |
| `resources`     | `() → Set<ResourceRef>`              | Todos os recursos presentes |
| `resourcesOf`   | `(id: AccountId) → Set<ResourceRef>` | Recursos de uma conta       |

#### Ledger

| Método                    | Assinatura                                       | Propósito                         |
|---------------------------|--------------------------------------------------|-----------------------------------|
| `ledgerHistory`           | `() → List<Transaction>`                         | Histórico completo                |
| `ledgerSize`              | `() → Int`                                       | Número de transações              |
| `getTransaction`          | `(id: TransactionId) → Transaction?`             | Busca por id                      |
| `transactionsByOperation` | `(operationId: OperationId) → List<Transaction>` | Transações de uma operação lógica |

#### Empréstimos (consulta)

| Método         | Assinatura                            | Propósito                |
|----------------|---------------------------------------|--------------------------|
| `getLoan`      | `(id: LoanId) → Loan?`                | Busca empréstimo         |
| `loanExists`   | `(id: LoanId) → Boolean`              | Verifica existência      |
| `loansOf`      | `(accountId: AccountId) → List<Loan>` | Empréstimos de uma conta |
| `allLoans`     | `() → List<Loan>`                     | Todos os empréstimos     |
| `loansByState` | `(state: LoanState) → List<Loan>`     | Filtra por estado        |

#### Operações econômicas (mutadoras, atômicas)

| Método        | Assinatura                                                                | Propósito                               |
|---------------|---------------------------------------------------------------------------|-----------------------------------------|
| `issue`       | `(to: AccountId, value: EconomicValue) → Transaction`                     | Cria valor em uma conta                 |
| `retire`      | `(from: AccountId, value: EconomicValue) → Transaction`                   | Destrói valor de uma conta              |
| `transfer`    | `(transfer: Transfer, charges: List<Charge> = emptyList()) → Transaction` | Transferência + cargos opcionais        |
| `exchange`    | `(exchange: Exchange, charges: List<Charge> = emptyList()) → Transaction` | Troca multi-perna + cargos              |
| `createLoan`  | `(creditor, debtor, principal, interest, dueDate) → Loan`                 | Cria empréstimo e transfere o principal |
| `payLoan`     | `(loanId: LoanId, amount: Money) → Loan`                                  | Aplica pagamento a um empréstimo aberto |
| `defaultLoan` | `(loanId: LoanId) → Loan`                                                 | Marca empréstimo como defaulted         |

---

### `Interest` (object)

**Propósito:**  
Utilitários puros de cálculo de juros (não tocam estado).

Métodos:

- `simple(principal: Money, rate: Quantity, periods: Int) → Money`  
  Fórmula: `principal × rate × periods`
- `compound(principal: Money, rate: Quantity, periods: Int) → Money`  
  Fórmula: `principal × ((1 + rate)^periods − 1)`

---

## Como Usar

1. **Crie a economia e as contas:**
   ```kotlin
   val economy = Economy()
   val alice = AccountId("alice")
   val bob   = AccountId("bob")
   economy.createAccount(alice)
   economy.createAccount(bob)
   ```

2. **Emita valor (crie dinheiro ou recursos):**
   ```kotlin
   val gold = Currency("GOLD")
   economy.issue(alice, EconomicValue.of(Money.of(1000, gold)))
   ```

3. **Transfira valor (com ou sem taxa):**
   ```kotlin
   val fee = Charge(
     from = alice,
     to = AccountId("treasury"),
     value = EconomicValue.of(Money.of(10, gold))
   )
   economy.transfer(
     Transfer(from = alice, to = bob, value = EconomicValue.of(Money.of(200, gold))),
     charges = listOf(fee)
   )
   ```

4. **Troca multi-perna (ex.: item por dinheiro):**
   ```kotlin
   val sword = ResourceRef("item", "sword-1")
   economy.exchange(
     Exchange(listOf(
       Transfer(bob, alice, EconomicValue.of(ResourceAmount.of(sword, 1))),
       Transfer(alice, bob, EconomicValue.of(Money.of(150, gold)))
     ))
   )
   ```

5. **Empréstimos:**
   ```kotlin
   val loan = economy.createLoan(
     creditor = alice,
     debtor = bob,
     principal = Money.of(500, gold),
     interest = Money.of(50, gold),
     dueDate = Instant.now().plusSeconds(86400 * 30)
   )
   economy.payLoan(loan.id, Money.of(200, gold))
   // ...
   economy.defaultLoan(loan.id) // se necessário
   ```

6. **Observe eventos:**
   ```kotlin
   val sub = economy.subscribe<EconomyEvent.TransactionCommitted> { event ->
     println("Tx ${event.transaction.id} commitada")
   }
   // ...
   sub.unsubscribe()
   ```

7. **Consulte saldos e histórico:**
   ```kotlin
   val balance = economy.balanceOf(alice)
   val history = economy.ledgerHistory()
   ```

Todas as operações mutadoras são atômicas: ou a transação inteira (incluindo cargos) é aplicada e registrada no ledger,
ou nenhuma mudança de estado ocorre. Eventos são publicados somente após o commit e fora do lock de escrita.