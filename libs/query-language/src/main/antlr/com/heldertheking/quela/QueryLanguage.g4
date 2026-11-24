grammar QueryLanguage;

// Conditions
query: andCondition EOF; // Entry point
andCondition: orCondition (AND orCondition)*;
orCondition: notCondition (OR notCondition)*;
notCondition: NOT? condition ;
condition: simpleCondition | inCondition | '(' andCondition ')';
simpleCondition: field operator literal;
inCondition: field IN '[' (literal (',' literal)*)? ']';

// Literals
literal:   STRING #stringLiteral
         | NUMBER #numberLiteral
         | BOOLEAN #booleanLiteral
         | NULL #nullLiteral;
field: IDENTIFIER ('.' IDENTIFIER)*;

// Relational operators
operator: '>'  #gt
        | '>=' #gte
        | '<'  #lt
        | '<=' #lte
        | '!=' #neq
        | '='  #eq;

// Keywords
AND: 'AND' | 'and';
OR: 'OR' | 'or';
NOT: 'NOT' | 'not' | '~';
EQUAL: '=';
IN: 'IN' | 'in';

// Literals
STRING: '\'' .*? '\'';
NUMBER: [0-9]+ ('.' [0-9]+)?;
BOOLEAN: 'true' | 'false';
NULL: 'null';

// Identifiers
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;

// Whitespace and comments
WS: [ \t\r\n]+ -> skip;
