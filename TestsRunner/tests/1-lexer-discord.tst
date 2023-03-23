!compiler_flags: --dump LEX --exec LEX

!name: Discord 1
!code:
!expected:
EOF:$
!end

!name: Discord 2
!code:
0 00 000
1 11 111
2 22 222
1 01 001
2 02 020

12345 67890
10001001010
00000110001
!expected:
[1:1-1:2] C_INTEGER:0
[1:3-1:5] C_INTEGER:00
[1:6-1:9] C_INTEGER:000
[2:1-2:2] C_INTEGER:1
[2:3-2:5] C_INTEGER:11
[2:6-2:9] C_INTEGER:111
[3:1-3:2] C_INTEGER:2
[3:3-3:5] C_INTEGER:22
[3:6-3:9] C_INTEGER:222
[4:1-4:2] C_INTEGER:1
[4:3-4:5] C_INTEGER:01
[4:6-4:9] C_INTEGER:001
[5:1-5:2] C_INTEGER:2
[5:3-5:5] C_INTEGER:02
[5:6-5:9] C_INTEGER:020
[7:1-7:6] C_INTEGER:12345
[7:7-7:12] C_INTEGER:67890
[8:1-8:12] C_INTEGER:10001001010
[9:1-9:12] C_INTEGER:00000110001
EOF:$
!end


!name: Discord 3
!code:
''
'besedilo'
'besedilo z ''narekovaji'''
' !"#$%&\()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~'
!expected:
[1:1-1:3] C_STRING:
[2:1-2:11] C_STRING:besedilo
[3:1-3:28] C_STRING:besedilo z 'narekovaji'
[4:1-4:99] C_STRING: !"#$%&\()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~
EOF:$
!end

!name: Discord 4
!code:
true false
false true

true true
false false
!expected:
[1:1-1:5] C_LOGICAL:true
[1:6-1:11] C_LOGICAL:false
[2:1-2:6] C_LOGICAL:false
[2:7-2:11] C_LOGICAL:true
[4:1-4:5] C_LOGICAL:true
[4:6-4:10] C_LOGICAL:true
[5:1-5:6] C_LOGICAL:false
[5:7-5:12] C_LOGICAL:false
EOF:$
!end

!name: Discord 5
!code:
arr else for fun if then typ var where while
logical integer string
!expected:
[1:1-1:4] KW_ARR:arr
[1:5-1:9] KW_ELSE:else
[1:10-1:13] KW_FOR:for
[1:14-1:17] KW_FUN:fun
[1:18-1:20] KW_IF:if
[1:21-1:25] KW_THEN:then
[1:26-1:29] KW_TYP:typ
[1:30-1:33] KW_VAR:var
[1:34-1:39] KW_WHERE:where
[1:40-1:45] KW_WHILE:while
[2:1-2:8] AT_LOGICAL:logical
[2:9-2:16] AT_INTEGER:integer
[2:17-2:23] AT_STRING:string
EOF:$
!end

!name: Discord 6
!code:
ime
Ime_S_Presledki
im3_k1_1m4_5t3v1lk3
whileSpremenljivka
integerSpremenljivka
!expected:
[1:1-1:4] IDENTIFIER:ime
[2:1-2:16] IDENTIFIER:Ime_S_Presledki
[3:1-3:20] IDENTIFIER:im3_k1_1m4_5t3v1lk3
[4:1-4:19] IDENTIFIER:whileSpremenljivka
[5:1-5:21] IDENTIFIER:integerSpremenljivka
EOF:$
!end

!name: Discord 7
!code:
+-*/%&|! ==!=<><=>=()[]{}:;.,=
!expected:
[1:1-1:2] OP_ADD:+
[1:2-1:3] OP_SUB:-
[1:3-1:4] OP_MUL:*
[1:4-1:5] OP_DIV:/
[1:5-1:6] OP_MOD:%
[1:6-1:7] OP_AND:&
[1:7-1:8] OP_OR:|
[1:8-1:9] OP_NOT:!
[1:10-1:12] OP_EQ:==
[1:12-1:14] OP_NEQ:!=
[1:14-1:15] OP_LT:<
[1:15-1:16] OP_GT:>
[1:16-1:18] OP_LEQ:<=
[1:18-1:20] OP_GEQ:>=
[1:20-1:21] OP_LPARENT:(
[1:21-1:22] OP_RPARENT:)
[1:22-1:23] OP_LBRACKET:[
[1:23-1:24] OP_RBRACKET:]
[1:24-1:25] OP_LBRACE:{
[1:25-1:26] OP_RBRACE:}
[1:26-1:27] OP_COLON::
[1:27-1:28] OP_SEMICOLON:;
[1:28-1:29] OP_DOT:.
[1:29-1:30] OP_COMMA:,
[1:30-1:31] OP_ASSIGN:=
EOF:$
!end

!name: Discord 8
!code:
# to je komentar, v katerem je lahko kar koli
ime1 ime2 ime3
# to je še zaključni komentar







# konec datoteke
!expected:
[2:1-2:5] IDENTIFIER:ime1
[2:6-2:10] IDENTIFIER:ime2
[2:11-2:15] IDENTIFIER:ime3
EOF:$
!end

!name: Discord 9
!code:
# preprost program
num:integer = 2;
zmnozek:integer = num*num;
sum:integer = 0;

# je to zanka?
for (i:integer = 0; (i > zmnozek) == false; i = i + 1) {
    sum = sum + i;
}

typ x: integer = 1345;
!expected:
[2:1-2:4] IDENTIFIER:num
[2:4-2:5] OP_COLON::
[2:5-2:12] AT_INTEGER:integer
[2:13-2:14] OP_ASSIGN:=
[2:15-2:16] C_INTEGER:2
[2:16-2:17] OP_SEMICOLON:;
[3:1-3:8] IDENTIFIER:zmnozek
[3:8-3:9] OP_COLON::
[3:9-3:16] AT_INTEGER:integer
[3:17-3:18] OP_ASSIGN:=
[3:19-3:22] IDENTIFIER:num
[3:22-3:23] OP_MUL:*
[3:23-3:26] IDENTIFIER:num
[3:26-3:27] OP_SEMICOLON:;
[4:1-4:4] IDENTIFIER:sum
[4:4-4:5] OP_COLON::
[4:5-4:12] AT_INTEGER:integer
[4:13-4:14] OP_ASSIGN:=
[4:15-4:16] C_INTEGER:0
[4:16-4:17] OP_SEMICOLON:;
[7:1-7:4] KW_FOR:for
[7:5-7:6] OP_LPARENT:(
[7:6-7:7] IDENTIFIER:i
[7:7-7:8] OP_COLON::
[7:8-7:15] AT_INTEGER:integer
[7:16-7:17] OP_ASSIGN:=
[7:18-7:19] C_INTEGER:0
[7:19-7:20] OP_SEMICOLON:;
[7:21-7:22] OP_LPARENT:(
[7:22-7:23] IDENTIFIER:i
[7:24-7:25] OP_GT:>
[7:26-7:33] IDENTIFIER:zmnozek
[7:33-7:34] OP_RPARENT:)
[7:35-7:37] OP_EQ:==
[7:38-7:43] C_LOGICAL:false
[7:43-7:44] OP_SEMICOLON:;
[7:45-7:46] IDENTIFIER:i
[7:47-7:48] OP_ASSIGN:=
[7:49-7:50] IDENTIFIER:i
[7:51-7:52] OP_ADD:+
[7:53-7:54] C_INTEGER:1
[7:54-7:55] OP_RPARENT:)
[7:56-7:57] OP_LBRACE:{
[8:5-8:8] IDENTIFIER:sum
[8:9-8:10] OP_ASSIGN:=
[8:11-8:14] IDENTIFIER:sum
[8:15-8:16] OP_ADD:+
[8:17-8:18] IDENTIFIER:i
[8:18-8:19] OP_SEMICOLON:;
[9:1-9:2] OP_RBRACE:}
[11:1-11:4] KW_TYP:typ
[11:5-11:6] IDENTIFIER:x
[11:6-11:7] OP_COLON::
[11:8-11:15] AT_INTEGER:integer
[11:16-11:17] OP_ASSIGN:=
[11:18-11:22] C_INTEGER:1345
[11:22-11:23] OP_SEMICOLON:;
EOF:$
!end