/* jQuery MaxLength for INPUT and TEXTAREA fields v1.0
* Last updated: Oct 15th, 2009. This notice must stay intact for usage
* Author: JavaScript Kit at http://www.javascriptkit.com/
* Visit http://www.javascriptkit.com/ for full source code
*/

var thresholdcolors=[['20%','darkred'], ['10%','red']] //[chars_left_in_pct, CSS color to apply to output]
var uncheckedkeycodes=/(8)|(13)|(16)|(17)|(18)/  //keycodes that are not checked, even when limit has been reached.

thresholdcolors.sort(function(a,b){return parseInt(a[0])-parseInt(b[0])}) //sort thresholdcolors by percentage, ascending

function setformfieldsize($fields, optsize, optoutputdiv){
	var $=jQuery
	$fields.each(function(i){
		var $field=$(this)
		$field.data('maxsize', optsize || parseInt($field.attr('data-maxsize'))) //max character limit
		var statusdivid=optoutputdiv || $field.attr('data-output') //id of DIV to output status
		$field.data('$statusdiv', $('#'+statusdivid).length==1? $('#'+statusdivid) : null)
		$field.unbind('keypress.restrict').bind('keypress.restrict', function(e){
			setformfieldsize.restrict($field, e)
		})
		$field.unbind('keyup.show').bind('keyup.show', function(e){
			setformfieldsize.showlimit($field)
		})
		setformfieldsize.showlimit($field) //show status to start
	})
}

setformfieldsize.restrict=function($field, e){
	var keyunicode=e.charCode || e.keyCode
	if (!uncheckedkeycodes.test(keyunicode)){
		if ($field.val().length >= $field.data('maxsize')){ //if characters entered exceed allowed
			if (e.preventDefault)
				e.preventDefault()
			return false
		}
	}
}

setformfieldsize.showlimit=function($field){
	if ($field.val().length > $field.data('maxsize')){
		var trimmedtext=$field.val().substring(0, $field.data('maxsize'))
		$field.val(trimmedtext)
	}
	if ($field.data('$statusdiv')){
		$field.data('$statusdiv').css('color', '').html($field.val().length)
		var pctremaining=($field.data('maxsize')-$field.val().length)/$field.data('maxsize')*100 //calculate chars remaining in terms of percentage
		for (var i=0; i<thresholdcolors.length; i++){
			if (pctremaining<=parseInt(thresholdcolors[i][0])){
				$field.data('$statusdiv').css('color', thresholdcolors[i][1])
				break
			}
		}
	}
}

jQuery(document).ready(function($){ //fire on DOM ready
	var $targetfields=$("input[data-maxsize], textarea[data-maxsize]") //get INPUTs and TEXTAREAs on page with "data-maxsize" attr defined
	setformfieldsize($targetfields)
})