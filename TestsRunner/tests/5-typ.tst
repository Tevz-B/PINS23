!compiler_flags: --dump TYP --exec TYP

!code:
typ a : arr[123] int;
typ int : integer;
fun h(x:a):int = 5;
fun h2(x:int):int = h(y) {where var y:arr[12] integer}
!failure:
99
!end

!code:
typ a : arr[123] int;
typ int : integer;
fun h(x:a):int = 5;
fun h2(x:int):int = h(y) {where var y:arr[123] string}
!failure:
99
!end

!code:
typ a3 : a;
typ a2: a3;
var x : a1;
fun f(x:integer):integer=5;
typ a1 : a2;
typ a : arr[2] a3
!failure:
99
!end
!code:
fun f2(x:int):int = ({{while true : x+x} = {while false : 2}}, x)
!failure:
99
!end

!code:
fun voidf(x:int):int={while true: 5}
!failure:
99
!end

!code:
fun nestedArr(x:arr[3] arr[4] arr[5] string):string = x[1][2][3];
fun callNestedArr(x:integer):string = nestedArr(y) {where
        var y : arr[3] arr[4] arr[5] integer} 
!failure:
99
!end

!code:
fun nestedArr(x:arr[3] arr[4] arr[5] string):string = x[1][2][3];
fun callNestedArr(x:integer):string = nestedArr(y) {where
        var y : arr[3] arr[4] arr[4] string} 
!failure:
99
!end

!code:
fun nestedArr(x:arr[3] arr[4] arr[5] string):integer = x[1][2][3];
fun callNestedArr(x:integer):integer = nestedArr(y) {where
        var y : arr[3] arr[4] arr[5] string}
!failure:
99
!end

-- !code:

-- !failure:
-- 99
-- !end

-- !code:

-- !failure:
-- 99
-- !end