{
  title: 'Webhook Proxy OPA',
  custom_action: true,
  custom_action_help: {
    learn_more_url: 'https://docs.workato.com/oem/oem-extensions.html',
    learn_more_text: 'Webhook Proxy documentation',
    body: '<p>Receive HTTP requests through a proxy and trigger Workato recipes.</p>'
  },
  secure_tunnel: true,
  connection: {
    fields: [
      { name: 'profile', optional: false,
        hint: 'Provide the webhook proxy extension connection profile.' },
      {
        name: 'connection_id', optional: false,
        hint: 'Provide a unique connection ID for this webhook proxy connection',
        label: 'Connection ID'
      },
      {
        name: 'listen_port',
        label: 'Listen port',
        type: 'string',
        optional: true,
        default: '8080',
        hint: 'Port number where the HTTP listener will accept requests (configured in OPA config.yml)'
      }
    ],
    authorization: {
      type: 'custom_auth',
      
      acquire: lambda do |connection|
        # No authentication needed, just verify connection
        {}
      end,
      
      apply: lambda do |_connection|
        headers('X-Workato-Connector': 'enforce')
      end
    },

    base_uri: lambda do |connection|
      "http://localhost/ext/#{connection['profile']}/"
    end
  },

  test: lambda do |connection|
    get('health').
      after_error_response(/.*/) do |_code, body, _header, message|
        error("Connection test failed: #{message}. Response: #{body}")
      end
  end,

  object_definitions: {
    webhook_event_output: {
      fields: lambda do |_connection, config_fields, _object_definitions|
        # By default, treat payload as a string
        # If custom schema is provided, use it instead
        custom_schema = config_fields['custom_schema']
        if custom_schema && !custom_schema.empty?
          parse_json(custom_schema)
        else
          [{ name: 'payload', label: 'Payload', type: 'string' }]
        end
      end
    }
  },

  actions: {},
  triggers: {
    new_http_event: {
      title: 'New HTTP event',
      subtitle: 'Triggers when an HTTP request is received by the proxy',
      description: "New <span class='provider'>HTTP event</span> received via " \
        "<span class='provider'>webhook proxy</span>",
      help: 'This trigger activates when the Java extension forwards an HTTP request to this webhook.',

      config_fields: [
        {
          name: 'custom_schema',
          label: 'Custom payload schema',
          control_type: 'schema-designer',
          optional: true,
          hint: 'Define the expected schema for the webhook payload.'
        }
      ],

      webhook_subscribe: lambda do |webhook_url, connection, input, recipe_id|
        # Register this connection with the extension so it knows where to forward requests
        # Extension stores: connection_id -> workato_webhook_url mapping
        post('webhook/subscribe').
          payload(
            workato_webhook_url: webhook_url,
            recipe_id: recipe_id,
            connection_id: connection['connection_id']
          ).
          after_error_response(/.*/) do |_code, body, _header, message|
            error("Failed to subscribe webhook: #{message}. Response: #{body}")
          end
      end,

      webhook_notification: lambda do |input, payload, extended_input_schema, extended_output_schema, headers, params|
        # Return payload with __event_id__ for deduplication
        # Extension generates X-Workato-Event-Id header which we extract here
        payload.merge({
          '__event_id__' => headers['X-Workato-Event-Id'] || headers['x-workato-event-id']
        })
      end,

      dedup: lambda do |record|
        # Use unique event ID from extension's X-Workato-Event-Id header for deduplication
        # This ensures each HTTP request triggers the recipe exactly once
        record['__event_id__']
      end,

      webhook_unsubscribe: lambda do |webhook_subscribe_output, connection|
        # Remove the connection_id mapping from extension when recipe stops
        post('webhook/unsubscribe').
          payload(
            recipe_id: webhook_subscribe_output['id'] || webhook_subscribe_output['recipe_id'],
            connection_id: connection['connection_id']
          ).
          after_error_response(/.*/) do |_code, body, _header, message|
            error("Failed to unsubscribe webhook: #{message}. Response: #{body}")
          end
      end,

      output_fields: lambda do |object_definitions|
        object_definitions['webhook_event_output']
      end,

      sample_output: lambda do
        # Sample output - actual structure depends on the incoming webhook payload
        {
          order_id: 'ORD-12345',
          customer_email: 'customer@example.com',
          amount: 99.99,
          timestamp: 1768544318
        }
      end
    }
  },
  methods: {
    required_extension_version: lambda do
      1
    end
  }
}
