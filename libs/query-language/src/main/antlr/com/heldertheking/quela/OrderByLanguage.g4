grammar OrderByLanguage;

sortCondition: (sortField (',' sortField)*)? EOF;
sortField: field order?;
field: IDENTIFIER ('.' IDENTIFIER)*;
order: ASC | DESC;
ASC: 'ASC' | 'asc';
DESC: 'DESC' | 'desc';
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;

// Whitespace and comments
WS: [ \t\r\n]+ -> skip;
