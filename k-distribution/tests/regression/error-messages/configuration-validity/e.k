// Copyright (c) 2016 K Team. All Rights Reserved.

module E-SYNTAX
    syntax Pgm
endmodule

module E
    imports E-SYNTAX

configuration
<T>
    <k> $PGM:Pgm </k>
    <a3>
        <a4>
            <a5> .K </a5>
        </a4>
    </a3>
    <b3>
        <b4>
            <b5> .K </b5>
        </b4>
        <c4> .K </c4>
    </b3>
</T>

rule <a5> 1 </a5> => <b5> 2 </b5>

endmodule
