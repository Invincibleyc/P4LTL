//#Nonterminating
/*
 * Date: 2018-11-21
 * Author: Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * 
 */


int main(void) {
	MY_LABEL:
	goto MY_LABEL;
    return 0;
}


