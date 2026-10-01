# AI-assisted development record

Created on 1 October 2026 during an AI-assisted portfolio workflow. The coding
agent implemented Java service boundaries, SQL transactions, React workflows,
tests and documentation. This does not establish the owner's prior production
experience or independent mastery of every technology.

Review focused on overlap intervals, conflicting clinicians/patients, replay
semantics, transactional rollback and race safety. A portability review replaced
case-sensitive JDBC map access with column-position row mapping. Tests and hosted
CI provide executable checks; they do not replace clinical or security review.

Interview preparation: trace `book` and `rebook`, explain why the mutex is acquired
before checking capacity, demonstrate a failed move preserving the original slot,
and discuss throughput, authentication and database migration limitations.
