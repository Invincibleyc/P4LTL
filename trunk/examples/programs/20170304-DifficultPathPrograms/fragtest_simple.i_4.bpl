implementation ULTIMATE.start() returns (){
    var main_#res : int;
    var main_#t~nondet0 : int;
    var main_#t~nondet1 : int;
    var main_#t~nondet2 : int;
    var main_~i~5 : int;
    var main_~pvlen~5 : int;
    var main_~tmp___1~5 : int;
    var main_~k~5 : int;
    var main_~n~5 : int;
    var main_~j~5 : int;
    var __VERIFIER_assert_#in~cond : int;
    var __VERIFIER_assert_~cond : int;

  loc0:
    havoc main_#res;
    havoc main_#t~nondet0, main_#t~nondet1, main_#t~nondet2, main_~i~5, main_~pvlen~5, main_~tmp___1~5, main_~k~5, main_~n~5, main_~j~5;
    havoc main_~i~5;
    havoc main_~pvlen~5;
    havoc main_~tmp___1~5;
    main_~k~5 := 0;
    havoc main_~n~5;
    main_~i~5 := 0;
    assume -2147483648 <= main_#t~nondet0 && main_#t~nondet0 <= 2147483647;
    main_~pvlen~5 := main_#t~nondet0;
    havoc main_#t~nondet0;
    assume true;
    assume -2147483648 <= main_#t~nondet1 && main_#t~nondet1 <= 2147483647;
    assume !(main_#t~nondet1 != 0 && main_~i~5 <= 1000000);
    havoc main_#t~nondet1;
    assume main_~i~5 > main_~pvlen~5;
    main_~pvlen~5 := main_~i~5;
    main_~i~5 := 0;
    goto loc1;
  loc1:
    assume true;
    assume -2147483648 <= main_#t~nondet2 && main_#t~nondet2 <= 2147483647;
    goto loc2;
  loc2:
    goto loc2_0, loc2_1;
  loc2_0:
    assume !!(main_#t~nondet2 != 0 && main_~i~5 <= 1000000);
    havoc main_#t~nondet2;
    main_~tmp___1~5 := main_~i~5;
    main_~i~5 := main_~i~5 + 1;
    main_~k~5 := main_~k~5 + 1;
    goto loc1;
  loc2_1:
    assume !(main_#t~nondet2 != 0 && main_~i~5 <= 1000000);
    havoc main_#t~nondet2;
    main_~j~5 := 0;
    main_~n~5 := main_~i~5;
    goto loc3;
  loc3:
    assume true;
    assume !false;
    __VERIFIER_assert_#in~cond := (if main_~k~5 >= 0 then 1 else 0);
    havoc __VERIFIER_assert_~cond;
    __VERIFIER_assert_~cond := __VERIFIER_assert_#in~cond;
    goto loc4;
  loc4:
    goto loc4_0, loc4_1;
  loc4_0:
    assume __VERIFIER_assert_~cond == 0;
    assume !false;
    goto loc5;
  loc4_1:
    assume !(__VERIFIER_assert_~cond == 0);
    main_~k~5 := main_~k~5 - 1;
    main_~i~5 := main_~i~5 - 1;
    main_~j~5 := main_~j~5 + 1;
    assume !(main_~j~5 >= main_~n~5);
    goto loc3;
  loc5:
    assert false;
}

procedure ULTIMATE.start() returns ();
modifies ;
modifies ;

