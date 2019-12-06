package com.internship.accesa.fooddeliveryauthserver.controller;

import com.internship.accesa.fooddeliveryauthserver.dto.UserDTO;
import com.internship.accesa.fooddeliveryauthserver.model.AuthProvider;
import com.internship.accesa.fooddeliveryauthserver.service.EmailService;
import com.internship.accesa.fooddeliveryauthserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
public class RegisterController {
	
	private BCryptPasswordEncoder bCryptPasswordEncoder;
	private UserService userService;
	private EmailService emailService;
	
	@Autowired
	public RegisterController(BCryptPasswordEncoder bCryptPasswordEncoder,
                              UserService userService, EmailService emailService) {
		this.bCryptPasswordEncoder = bCryptPasswordEncoder;
		this.userService = userService;
		this.emailService = emailService;
	}
	
	// Return registration form template
	@RequestMapping(value="/register", method = RequestMethod.GET)
	public ModelAndView showRegistrationPage(ModelAndView modelAndView, UserDTO user){
		modelAndView.addObject("user", user);
		modelAndView.setViewName("register");
		return modelAndView;
	}
	
	// Process form input data
	@RequestMapping(value = "/register", method = RequestMethod.POST)
	public ModelAndView processRegistrationForm(ModelAndView modelAndView, @Valid UserDTO user, BindingResult bindingResult, HttpServletRequest request) {
				
		// Lookup user in database by e-mail
		Optional<UserDTO> userExists = userService.findByEmail(user.getEmail());
		
		if (userExists.isPresent()) {
			modelAndView.addObject("alreadyRegisteredMessage", "Oops!  There is already a user registered with the email provided.");
			bindingResult.reject("email");
		} else { // new user so we create user and send confirmation e-mail

			// Generate random 36-character string token for confirmation link
			user.setConfirmationToken(UUID.randomUUID().toString());

			user.setProvider(AuthProvider.local);

			user = userService.save(user);

			//User will have a default password initially
			String appUrl = request.getHeader("origin") + request.getContextPath();

			SimpleMailMessage registrationEmail = new SimpleMailMessage();
			registrationEmail.setTo(user.getEmail());
			registrationEmail.setSubject("Registration Confirmation");
			registrationEmail.setText("To confirm your e-mail address, please click the link below:\n"
					+ appUrl + "/confirm?token=" + user.getConfirmationToken());

			emailService.sendEmail(registrationEmail);

			modelAndView.addObject("confirmationMessage", "A confirmation e-mail has been sent to " + user.getEmail());
		}
		modelAndView.addObject("user", user);
		modelAndView.setViewName("register");
		return modelAndView;
	}


	// Process confirmation link
	@RequestMapping(value="/confirm", method = RequestMethod.GET)
	public ModelAndView confirmRegistration(ModelAndView modelAndView, @RequestParam("token") String token) {

		Optional<UserDTO> user = userService.findByConfirmationToken(token);

		if (!user.isPresent()) { // No token found in DB
			modelAndView.addObject("invalidToken", "Oops!  This is an invalid confirmation link.");
		} else { // Token found
			modelAndView.addObject("token", user.get().getConfirmationToken());
		}
		return modelAndView;
	}

	// Process confirmation link
	@RequestMapping(value="/confirm", method = RequestMethod.POST)
	public ModelAndView confirmRegistration(ModelAndView modelAndView, @RequestParam Map<String, String> requestParams) {

		// Find the user associated with the reset token
		Optional<UserDTO> optionalUserDTO = userService.findByConfirmationToken(requestParams.get("token"));

		if(optionalUserDTO.isPresent()){
			UserDTO userDTO = optionalUserDTO.get();
			userDTO.setPassword(bCryptPasswordEncoder.encode(requestParams.get("password")));
			// Set email verified
			userDTO.emailVerified(true);
			// Save user
			userService.save(userDTO);
			modelAndView.addObject("successMessage", "Your password has been set!");
		}else {
			modelAndView.addObject("errorMessage", "Your password has been set! ");
		}

		modelAndView.setViewName("confirm");

		return modelAndView;
	}



}