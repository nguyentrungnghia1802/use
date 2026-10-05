# Historical runtime fixtures

The V1/V2 connector, mapping, mutation and verification implementation lives only
in test scope to reproduce its frozen evidence. Production uses the Bridge
contract and `codegrounded.runtime` on the active USE Session. These test classes
are not shipped, registered or selected as a fallback.
