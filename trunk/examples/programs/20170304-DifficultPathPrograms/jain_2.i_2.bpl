implementation ULTIMATE.start() returns (){
    var main_#res : int;
    var main_#t~nondet0 : int;
    var main_#t~nondet1 : int;
    var main_~x~5 : int;
    var main_~y~5 : int;
    var __VERIFIER_assert_#in~cond : int;
    var __VERIFIER_assert_~cond : int;

  loc0:
    havoc main_#res;
    havoc main_#t~nondet0, main_#t~nondet1, main_~x~5, main_~y~5;
    havoc main_~x~5;
    havoc main_~y~5;
    main_~x~5 := 1;
    main_~y~5 := 1;
    goto loc1;
  loc1:
    assume true;
    assume !false;
    main_~x~5 := main_~x~5 + 2 * main_#t~nondet0;
    havoc main_#t~nondet0;
    main_~y~5 := main_~y~5 + 2 * main_#t~nondet1;
    havoc main_#t~nondet1;
    __VERIFIER_assert_#in~cond := (if (main_~x~5 + main_~y~5) % 4294967296 != 1 then 1 else 0);
    havoc __VERIFIER_assert_~cond;
    __VERIFIER_assert_~cond := __VERIFIER_assert_#in~cond;
    goto loc2;
  loc2:
    goto loc2_0, loc2_1;
  loc2_0:
    assume !(__VERIFIER_assert_~cond == 0);
    goto loc1;
  loc2_1:
    assume __VERIFIER_assert_~cond == 0;
    assume !false;
    goto loc3;
  loc3:
    assert false;
}

procedure ULTIMATE.start() returns ();
modifies ;
modifies ;

