# FL-Economy — Referência Rápida da API

**Package:** `com.lucasalfare.fleconomy`

## 1. Identificadores

### `AccountId`

`value: String`

ID não vazio de uma conta.

### `TransactionId`

`value: String`

ID não vazio de uma transação registrada no ledger.

### `OperationId`

`value: String`

ID lógico de uma operação. Uma mesma operação pode possuir várias `Transaction`.

### `LoanId`

`value: String`

ID não vazio de um empréstimo.

---

# 2. Valores econômicos

### `Quantity`

Quantidade decimal não negativa baseada em `BigDecimal`.

Principais APIs:

* `Quantity.ZERO`
* `Quantity.of(BigDecimal)`
* `Quantity.of(Long)`
* `Quantity.of(String)`
* `+`
* `-`
* `compareTo`
* `isZero()`
* `isPositive()`

Subtração que produziria valor negativo lança `IllegalArgumentException`.

A igualdade ignora zeros finais do `BigDecimal`.

---

### `Currency`

`code: String`

Identificador não vazio de moeda.

A biblioteca trata moedas como identificadores opacos. Não existe conversão automática entre moedas.

---

### `Money`

* `quantity: Quantity`
* `currency: Currency`

Valor monetário não negativo.

Operações:

* `+`
* `-`
* `compareTo`
* `isZero()`
* `isPositive()`

`+`, `-` e comparação exigem a mesma moeda.

Fábricas:

* `Money.of(BigDecimal, Currency)`
* `Money.of(Long, Currency)`
* `Money.of(String, Currency)`
* `Money.zero(Currency)`

---

### `ResourceRef`

* `type: String`
* `id: String`

Identificador de um recurso não monetário. Ambos não podem ser vazios.

---

### `ResourceAmount`

* `resource: ResourceRef`
* `quantity: Quantity`

Quantidade de um recurso específico.

Operações:

* `+`
* `-`
* `compareTo`
* `isZero()`
* `isPositive()`

As operações exigem o mesmo `ResourceRef`.

Fábricas:

* `of(type, id, BigDecimal)`
* `of(type, id, Long)`
* `of(type, id, String)`
* `of(resource, BigDecimal)`
* `of(resource, Long)`
* `of(resource, String)`
* `zero(resource)`

---

# 3. `EconomicValue`

`sealed class`

Representa genericamente um valor econômico.

### `EconomicValue.Monetary`

`money: Money`

### `EconomicValue.Resource`

`amount: ResourceAmount`

Fábricas:

* `EconomicValue.of(Money)`
* `EconomicValue.of(ResourceAmount)`

---

# 4. `Balance`

Snapshot imutável de uma conta.

* `moneys: Map<Currency, Quantity>`
* `resources: Map<ResourceRef, Quantity>`

APIs:

* `moneyOf(currency): Quantity`
* `resourceOf(resource): Quantity`
* `isEmpty()`

Ausência no mapa significa quantidade zero.

`Balance.EMPTY` representa saldo vazio.

---

# 5. `Account`

Conta econômica pertencente a uma `Economy`.

`id: AccountId`

O construtor é `internal`: contas devem ser criadas por `Economy.createAccount`.

### API pública

`balance(): Balance`

Retorna snapshot imutável do saldo atual.

A conta não expõe operações públicas de alteração de saldo. Toda mutação ocorre por operações da `Economy`.

---

# 6. Movimentações e operações

### `Movement`

Movimento elementar de valor:

* `from: AccountId?`
* `to: AccountId?`
* `value: EconomicValue`

Formas:

* `from != null`, `to != null` → transferência;
* `from == null`, `to != null` → emissão;
* `from != null`, `to == null` → retirada.

Não pode ter ambos nulos.

---

### `Transfer`

* `from: AccountId`
* `to: AccountId`
* `value: EconomicValue`

Descrição de uma transferência entre contas distintas.

---

### `Exchange`

`transfers: List<Transfer>`

Exchange precisa conter pelo menos uma transferência.

Todos os transfers são aplicados atomicamente.

---

### `Charge`

* `from`
* `to`
* `value`

Carga adicional anexada a `transfer` ou `exchange`.

Pode representar taxa, imposto, comissão, penalidade etc.

---

### `Transaction`

Registro imutável de uma operação efetivamente commitada.

* `id: TransactionId`
* `operationId: OperationId`
* `movements: List<Movement>`
* `timestamp: Instant`

Depois de registrada no ledger, não é modificada.

---

# 7. Empréstimos

### `LoanState`

* `OPEN`
* `PAID`
* `DEFAULTED`

### `Loan`

* `id`
* `creditor`
* `debtor`
* `principal: Money`
* `interest: Money`
* `dueDate: Instant`
* `paid: Money`
* `state: LoanState`

APIs:

`amountDue(): Money`

Calcula:

`principal + interest - paid`

`isFullyPaid(): Boolean`

Retorna `true` quando `paid >= principal + interest`.

---

# 8. `Economy`

Núcleo da biblioteca.

É **thread-safe** e todas as operações mutáveis são atômicas para a instância inteira.

Leitores podem executar concorrentemente; escritores usam acesso exclusivo.

Cada `Economy` é totalmente independente das demais.

---

## Contas

`createAccount(id): Account`

Cria conta vazia.

ID duplicado → `IllegalArgumentException`.

`getAccount(id): Account?`

`accountExists(id): Boolean`

`accountIds(): Set<AccountId>`

`accounts(): List<AccountId>`

`balanceOf(id): Balance`

Conta inexistente → `IllegalArgumentException`.

`balances(): Map<AccountId, Balance>`

Snapshot dos saldos de todas as contas.

---

## Moedas e recursos

`currencies(): Set<Currency>`

Todas as moedas presentes em alguma conta.

`currenciesOf(id): Set<Currency>`

Moedas de uma conta específica.

Conta inexistente → `IllegalArgumentException`.

`resources(): Set<ResourceRef>`

Todos os recursos presentes em alguma conta.

`resourcesOf(id): Set<ResourceRef>`

Recursos de uma conta específica.

Conta inexistente → `IllegalArgumentException`.

---

# 9. Ledger

O ledger é append-only.

### `ledgerHistory(): List<Transaction>`

Histórico completo.

### `ledgerSize(): Int`

Número de transações commitadas.

### `getTransaction(id): Transaction?`

Busca por ID.

### `transactionsByOperation(operationId): List<Transaction>`

Retorna as transações pertencentes à mesma `OperationId`.

---

# 10. Emissão e retirada

### `issue(to, value): Transaction`

Cria valor:

`null → account`

A quantidade precisa ser não negativa.

Gera automaticamente:

* `OperationId`
* `TransactionId`
* timestamp com `Instant.now()`

É atômico.

---

### `retire(from, value): Transaction`

Destrói valor:

`account → null`

Exige saldo/recurso suficiente.

É atômico.

---

# 11. Transferência

### `transfer(transfer, charges = emptyList()): Transaction`

Executa:

* transferência principal;
* todas as charges.

Tudo pertence à **mesma `Transaction`** e é aplicado atomicamente.

Valida:

* contas existentes;
* contas de origem e destino diferentes;
* valores não negativos;
* saldo suficiente para todos os débitos.

Falha em qualquer validação → nenhuma alteração de saldo.

---

# 12. Exchange

### `exchange(exchange, charges = emptyList()): Transaction`

Executa múltiplas transferências e charges como uma única operação atômica.

Todas as movimentações são validadas antes da aplicação.

Falha em qualquer etapa → nenhum saldo é alterado.

A ordem dos `Movement` na `Transaction` é:

1. transfers do `Exchange`;
2. charges.

---

# 13. Empréstimos

### `createLoan(...)`

Parâmetros:

* `creditor`
* `debtor`
* `principal: Money`
* `interest: Money`
* `dueDate`

Regras:

* creditor ≠ debtor;
* ambas as contas existem;
* principal e interest usam a mesma moeda;
* principal > 0;
* interest ≥ 0.

Ao criar:

1. principal é transferido de creditor para debtor;
2. loan é criado como `OPEN`;
3. `paid` começa em zero.

A transferência do principal gera uma `Transaction` normal no ledger.

---

### `getLoan(id): Loan?`

### `loanExists(id): Boolean`

### `loansOf(accountId): List<Loan>`

Retorna empréstimos onde a conta é creditor ou debtor.

### `allLoans(): List<Loan>`

Todos os empréstimos.

### `loansByState(state): List<Loan>`

Filtra por estado.

---

### `payLoan(loanId, amount): Loan`

Pagamento de empréstimo aberto.

O pagamento é uma transferência normal:

`debtor → creditor`

Regras:

* loan existe;
* loan está `OPEN`;
* moeda coincide;
* valor > 0;
* pagamento não pode exceder `amountDue()`.

Depois do pagamento:

* se totalmente quitado → `PAID`;
* caso contrário → permanece `OPEN`.

---

### `defaultLoan(loanId): Loan`

Marca empréstimo `OPEN` como `DEFAULTED`.

Não altera saldos.

As consequências econômicas de um default ficam por conta da aplicação.

---

# 14. Juros

### `Interest`

Objeto utilitário puro.

Não modifica contas, loans ou ledger.

### `Interest.simple(principal, rate, periods): Money`

Fórmula:

`principal × rate × periods`

Regras:

* `periods >= 0`;
* `rate` não é negativo;
* resultado usa a mesma moeda do principal.

Retorna zero quando:

* periods = 0;
* principal = 0;
* rate = 0.

### `Interest.compound(principal, rate, periods): Money`

Fórmula:

`principal × ((1 + rate)^periods − 1)`

Regras:

* `periods >= 0`;
* rate não é negativo;
* mesma moeda do principal.

Retorna zero para `periods = 0` ou principal zero.

---

# 15. Atomicidade

Toda operação mutável da `Economy` é atômica em relação à instância inteira.

O mecanismo:

1. valida todas as movimentações;
2. calcula saldos projetados;
3. somente depois altera as contas;
4. adiciona a `Transaction` ao ledger.

Portanto nenhuma operação parcialmente aplicada fica visível para outros leitores.

---

# 16. Modelo mental rápido

### Valor

`Quantity → Money / ResourceAmount → EconomicValue`

### Conta

`Account → Balance`

### Movimento

`Movement`

### Operação

`Transfer / Exchange / Charge`

### Registro

`Transaction → Ledger`

### Crédito

`issue`

### Destruição

`retire`

### Transferência

`transfer`

### Operação multi-leg

`exchange`

### Crédito parcelado / obrigação

`Loan`

### Cálculo financeiro

`Interest`

### Regra central

**A aplicação define o significado econômico; `Economy` garante armazenamento, validação, atomicidade, concorrência e
histórico.**
